package com.tenure.domain.payment.service;

import com.tenure.domain.offer.entity.PurchaseOffer;
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
 * 결제 승인·확정·해제. PG 연동이 없어 승인은 항상 성공하는 mock 임 — 실제 PG 를 붙이는 시점에
 * authorize 내부만 교체하면 됨
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

	private final PaymentRepository paymentRepository;

	/** 제안 전송과 같은 트랜잭션에서 결제 승인을 임시로 확보함 */
	@Transactional
	@Loggable("결제 승인")
	public Payment authorize(PurchaseOffer offer, PaymentMethod method, Integer amount, LocalDateTime now) {
		return paymentRepository.save(Payment.authorize(offer, method, amount, now));
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
		Payment payment = findByOfferIdForUpdate(offerId);
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
	 * 확정(CAPTURED)된 결제는 해제하지 않음 — 그 결제를 되돌리는 경로는 거래 취소·환불뿐임
	 */
	@Transactional
	@Loggable("결제 승인 해제")
	public void release(Long offerId, LocalDateTime now) {
		Payment payment = findByOfferIdForUpdate(offerId);
		// 이미 해제됐으면 멱등하게 끝냄 — 호출부가 중복 호출을 걸러내지 않아도 됨
		if (payment.getStatus() == PaymentStatus.RELEASED) {
			return;
		}
		if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
			log.info("승인 상태가 아닌 결제를 해제하려 함 offerId={} status={}", offerId, payment.getStatus());
			throw new CustomException(PaymentErrorCode.PAYMENT_409_CAPTURE);
		}
		payment.release(now);
	}

	/**
	 * 상품 발송 전 거래 취소 시 최종 결제를 환불함.
	 *
	 * 행 잠금 조회라 동시 취소 경합을 여기서도 막음. CAPTURED 가 아니면 거부함 — 아직 확정되지
	 * 않았거나 이미 환불된 결제를 다시 환불하면 상태가 어긋난다
	 */
	@Transactional
	@Loggable("결제 환불")
	public void refund(Long offerId, LocalDateTime now) {
		Payment payment = findByOfferIdForUpdate(offerId);
		if (payment.getStatus() != PaymentStatus.CAPTURED) {
			log.info("확정 상태가 아닌 결제를 환불하려 함 offerId={} status={}", offerId, payment.getStatus());
			throw new CustomException(PaymentErrorCode.PAYMENT_409_REFUND);
		}
		payment.refund(now);
	}

	private Payment findByOfferIdForUpdate(Long offerId) {
		return paymentRepository.findByOfferIdForUpdate(offerId)
				.orElseThrow(() -> {
					log.info("제안에 연결된 결제가 없음 offerId={}", offerId);
					return new CustomException(PaymentErrorCode.PAYMENT_409_CAPTURE);
				});
	}
}
