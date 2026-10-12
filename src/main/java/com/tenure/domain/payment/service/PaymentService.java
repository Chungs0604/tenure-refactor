package com.tenure.domain.payment.service;

import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.payment.client.ConfirmOutcome;
import com.tenure.domain.payment.client.TossConfirmResponse;
import com.tenure.domain.payment.client.TossPaymentClient;
import com.tenure.domain.payment.entity.Payment;
import com.tenure.domain.payment.enums.PaymentMethod;
import com.tenure.domain.payment.enums.PaymentStatus;
import com.tenure.domain.payment.exception.PaymentErrorCode;
import com.tenure.domain.payment.repository.PaymentRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 승인·확정·해제·환불. 토스페이먼츠(간편결제) 연동.
 *
 * 간편결제는 confirm 호출 즉시 최종 결제가 끝나 "임시 승인만" 해두는 단계가 없다 — 그래서
 * authorize 가 confirm 을, release·refund 가 cancel(환불)을 실제로 호출한다. capture 는 우리
 * 쪽 상태 전이일 뿐 PG 호출이 없다 — 돈은 이미 authorize 시점에 받았다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final TossPaymentClient tossPaymentClient;

	/**
	 * 토스페이먼츠 결제 승인(confirm) 호출. DB 쓰기가 없어 호출자가 아직 아무것도 저장하지
	 * 않은 시점에, 즉 실패해도 되돌릴 게 없는 시점에 먼저 불러야 한다 — {@code authorize()} 가
	 * 그 결과를 받아 Payment 를 저장한다.
	 *
	 * 토스가 명시적으로 거부(Rejected)한 경우만 PAYMENT_402 다. 타임아웃·5xx 처럼 응답을 못
	 * 받은 경우(Unknown)는 실제로 승인됐을 수 있어 다른 코드(PAYMENT_503_CONFIRM_UNKNOWN)로
	 * 구분한다 — 여길 PAYMENT_402 로 뭉개면, 승인은 됐는데 우리만 "실패"로 안내하고 끝난다
	 */
	@Loggable("토스페이먼츠 결제 승인 호출")
	public TossConfirmResponse confirmWithPg(String paymentKey, String orderId, Integer amount) {
		ConfirmOutcome outcome = tossPaymentClient.confirm(paymentKey, orderId, amount);
		return switch (outcome) {
			case ConfirmOutcome.Success success -> success.response();
			case ConfirmOutcome.Rejected rejected -> {
				log.info("토스페이먼츠 결제 승인이 거부됨 orderId={}", orderId);
				throw new CustomException(PaymentErrorCode.PAYMENT_402);
			}
			case ConfirmOutcome.Unknown unknown -> {
				log.error("토스페이먼츠 결제 승인 결과를 확인할 수 없음 — 수동 확인 필요 orderId={}", orderId);
				throw new CustomException(PaymentErrorCode.PAYMENT_503_CONFIRM_UNKNOWN);
			}
		};
	}

	/** confirmWithPg 가 이미 받아 둔 승인 결과를 Payment 로 저장함 */
	@Transactional
	@Loggable("결제 승인 저장")
	public Payment authorize(
			PurchaseOffer offer, PaymentMethod method, Integer amount,
			TossConfirmResponse confirmed, LocalDateTime now) {
		return paymentRepository.save(Payment.authorize(
				offer, method, confirmed.displayMethod(), amount, confirmed.paymentKey(), now));
	}

	/**
	 * confirmWithPg 는 성공했지만 그 뒤(제안 저장 등)가 실패해 Payment 를 만들 수 없게 된
	 * 경우의 보정 환불. 실패해도 예외를 던지지 않는다 — 이미 다른 예외(OFFER_409_ALREADY_USED
	 * 등)로 응답이 끝나는 경로라, 여기서 또 던지면 원래 에러가 가려진다. 저장된 Payment 가
	 * 없어 재시도할 방법이 없으니 실패하면 로그로만 남겨 수동 확인을 유도한다
	 */
	public void compensateFailedAuthorization(String paymentKey) {
		if (!tossPaymentClient.cancel(paymentKey, "제안 저장 실패에 따른 환불")) {
			log.error("승인은 성공했지만 보정 환불에 실패함 — 수동 확인 필요 paymentKey={}", paymentKey);
		}
	}

	/**
	 * 판매자 수락 시 최종 결제로 확정함. 호출자가 뒤이어 order 를 연결할 수 있게 엔티티를 돌려줌.
	 *
	 * 행 잠금 조회라 동시 확정·해제 경합을 여기서도 막음. AUTHORIZED 가 아니면 거부함 — 이미
	 * 해제된 결제를 확정하면 '승인 없는 결제 완료'가 됨
	 */
	@Transactional
	@Loggable("결제 확정")
	public Payment capture(Long offerId, LocalDateTime now) {
		// 잠금 재조회 — 같은 결제를 동시에 해제하려는 경합이 있으면 여기서 먼저 걸림
		Payment payment = findByOfferIdForUpdate(offerId, PaymentErrorCode.PAYMENT_409_CAPTURE);
		if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
			log.info("승인 상태가 아닌 결제를 확정하려 함 offerId={} status={}", offerId, payment.getStatus());
			throw new CustomException(PaymentErrorCode.PAYMENT_409_CAPTURE);
		}
		payment.capture(now);
		return payment;
	}

	/**
	 * 거절·취소·만료 시 임시 승인을 해제함. 이미 해제된 결제는 다시 건드리지 않음.
	 *
	 * 간편결제는 confirm 시점에 이미 최종 결제가 끝나 있어, 여기서 토스 cancel(환불)을 실제로
	 * 호출해야 돈이 돌아간다 — 상태만 RELEASED 로 바꾸면 우리 시스템과 토스 양쪽이 어긋난다.
	 * cancel 이 실패하면 상태를 바꾸지 않고 던진다 — 환불이 안 됐는데 '해제됨'으로 남으면 안 된다.
	 *
	 * {@code pgTransactionId} 가 없으면(토스 연동 전 mock 시절 승인) 실제로 받은 돈이 없다는
	 * 뜻이라 PG 호출을 건너뛰고 상태만 전이한다 — 그대로 호출하면 {@code /v1/payments/null/cancel}
	 * 로 나가 영구히 거부당한다.
	 *
	 * 확정(CAPTURED)된 결제는 해제하지 않음 — 그 결제를 되돌리는 경로는 거래 취소·환불뿐임
	 */
	@Transactional
	@Loggable("결제 승인 해제")
	public void release(Long offerId, LocalDateTime now) {
		Payment payment = findByOfferIdForUpdate(offerId, PaymentErrorCode.PAYMENT_409_RELEASE);
		// 이미 해제됐으면 멱등하게 끝냄 — 호출부가 중복 호출을 걸러내지 않아도 됨
		if (payment.getStatus() == PaymentStatus.RELEASED) {
			return;
		}
		if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
			log.info("승인 상태가 아닌 결제를 해제하려 함 offerId={} status={}", offerId, payment.getStatus());
			throw new CustomException(PaymentErrorCode.PAYMENT_409_RELEASE);
		}
		if (payment.getPgTransactionId() != null
				&& !tossPaymentClient.cancel(payment.getPgTransactionId(), "제안 거절/취소/만료")) {
			log.warn("토스페이먼츠 결제 취소(해제)에 실패함 offerId={}", offerId);
			throw new CustomException(PaymentErrorCode.PAYMENT_409_RELEASE);
		}
		payment.release(now);
	}

	/**
	 * 상품 발송 전 거래 취소 시 최종 결제를 환불함. release 와 같은 이유로 토스 cancel 을
	 * 실제로 호출한다 — 실패하면 상태를 바꾸지 않고 던진다.
	 *
	 * 행 잠금 조회라 동시 취소 경합을 여기서도 막음. CAPTURED 가 아니면 거부함 — 아직 확정되지
	 * 않았거나 이미 환불된 결제를 다시 환불하면 상태가 어긋난다.
	 *
	 * {@code pgTransactionId} 가 없으면(mock 시절 승인) release() 와 같은 이유로 PG 호출을
	 * 건너뛴다
	 */
	@Transactional
	@Loggable("결제 환불")
	public void refund(Long offerId, LocalDateTime now) {
		Payment payment = findByOfferIdForUpdate(offerId, PaymentErrorCode.PAYMENT_409_REFUND);
		if (payment.getStatus() != PaymentStatus.CAPTURED) {
			log.info("확정 상태가 아닌 결제를 환불하려 함 offerId={} status={}", offerId, payment.getStatus());
			throw new CustomException(PaymentErrorCode.PAYMENT_409_REFUND);
		}
		if (payment.getPgTransactionId() != null
				&& !tossPaymentClient.cancel(payment.getPgTransactionId(), "상품 발송 전 거래 취소")) {
			log.warn("토스페이먼츠 결제 취소(환불)에 실패함 offerId={}", offerId);
			throw new CustomException(PaymentErrorCode.PAYMENT_409_REFUND);
		}
		payment.refund(now);
	}

	/** 호출자(확정·해제·환불)마다 성격이 다른 409 를 쓰므로, 못 찾았을 때 던질 코드를 받는다. */
	private Payment findByOfferIdForUpdate(Long offerId, PaymentErrorCode notFoundCode) {
		return paymentRepository.findByOfferIdForUpdate(offerId)
				.orElseThrow(() -> {
					log.info("제안에 연결된 결제가 없음 offerId={}", offerId);
					return new CustomException(notFoundCode);
				});
	}
}
