package com.tenure.domain.payment.service;

import com.tenure.domain.payment.client.ConfirmOutcome;
import com.tenure.domain.payment.client.TossConfirmResponse;
import com.tenure.domain.payment.client.TossPaymentClient;
import com.tenure.domain.payment.entity.Payment;
import com.tenure.domain.payment.enums.PaymentMethod;
import com.tenure.domain.payment.enums.PaymentStatus;
import com.tenure.domain.payment.exception.PaymentErrorCode;
import com.tenure.domain.payment.repository.PaymentRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * capture·release·refund 가 저마다 다른 409 를 쓰는지가 핵심 — 못 찾았을 때·상태가 안 맞을 때
 * 모두 호출부 맥락에 맞는 ErrorCode 여야 한다. release·refund 는 토스 cancel 이 실패하면
 * 상태를 바꾸지 않고 던지는지도 함께 본다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

	private static final Long OFFER_ID = 1L;
	private static final String PAYMENT_KEY = "test-payment-key";
	private static final String ORDER_ID = "test-order-id";

	@Mock private PaymentRepository paymentRepository;
	@Mock private TossPaymentClient tossPaymentClient;

	@InjectMocks private PaymentService paymentService;

	@Nested
	@DisplayName("결제 확정")
	class Capture {

		@Test
		@DisplayName("AUTHORIZED 상태면 CAPTURED 로 바뀐다 — 돈은 이미 승인 시점에 받았으므로 PG 를 다시 부르지 않는다")
		void capture_authorized_becomesCaptured() {
			Payment payment = authorizedPayment();
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.of(payment));

			paymentService.capture(OFFER_ID, LocalDateTime.now());

			assertThat(payment.getCapturedAt()).isNotNull();
			verifyNoInteractions(tossPaymentClient);
		}

		@Test
		@DisplayName("연결된 결제가 없으면 PAYMENT_409_CAPTURE 이고 PG 를 부르지 않는다")
		void capture_noPayment_throwsCaptureConflict() {
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> paymentService.capture(OFFER_ID, LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.extracting(PaymentServiceTest::errorCodeOf)
					.isEqualTo(PaymentErrorCode.PAYMENT_409_CAPTURE);
			verifyNoInteractions(tossPaymentClient);
		}

		@Test
		@DisplayName("AUTHORIZED 가 아니면 PAYMENT_409_CAPTURE 이고 PG 를 부르지 않는다")
		void capture_notAuthorized_throwsCaptureConflict() {
			Payment payment = releasedPayment();
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.of(payment));

			assertThatThrownBy(() -> paymentService.capture(OFFER_ID, LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.extracting(PaymentServiceTest::errorCodeOf)
					.isEqualTo(PaymentErrorCode.PAYMENT_409_CAPTURE);
			verifyNoInteractions(tossPaymentClient);
		}
	}

	@Nested
	@DisplayName("결제 승인 해제")
	class Release {

		@Test
		@DisplayName("AUTHORIZED 상태면 토스 취소를 호출하고 RELEASED 로 바뀐다")
		void release_authorized_becomesReleased() {
			Payment payment = authorizedPayment();
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.of(payment));
			given(tossPaymentClient.cancel(PAYMENT_KEY, "제안 거절/취소/만료")).willReturn(true);

			paymentService.release(OFFER_ID, LocalDateTime.now());

			assertThat(payment.getReleasedAt()).isNotNull();
		}

		@Test
		@DisplayName("이미 RELEASED 면 토스를 다시 호출하지 않고 멱등하게 끝난다")
		void release_alreadyReleased_doesNothing() {
			Payment payment = releasedPayment();
			LocalDateTime firstReleasedAt = payment.getReleasedAt();
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.of(payment));

			paymentService.release(OFFER_ID, LocalDateTime.now());

			assertThat(payment.getReleasedAt()).isEqualTo(firstReleasedAt);
		}

		@Test
		@DisplayName("연결된 결제가 없으면 PAYMENT_409_CAPTURE 가 아니라 PAYMENT_409_RELEASE 이다")
		void release_noPayment_throwsReleaseConflict() {
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> paymentService.release(OFFER_ID, LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.extracting(PaymentServiceTest::errorCodeOf)
					.isEqualTo(PaymentErrorCode.PAYMENT_409_RELEASE);
		}

		@Test
		@DisplayName("CAPTURED 상태면 PAYMENT_409_RELEASE 이다 — 확정된 결제는 해제 경로로 되돌리지 않는다")
		void release_captured_throwsReleaseConflict() {
			Payment payment = capturedPayment();
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.of(payment));

			assertThatThrownBy(() -> paymentService.release(OFFER_ID, LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.extracting(PaymentServiceTest::errorCodeOf)
					.isEqualTo(PaymentErrorCode.PAYMENT_409_RELEASE);
		}

		@Test
		@DisplayName("토스 취소 호출이 실패하면 상태를 바꾸지 않고 PAYMENT_409_RELEASE 이다")
		void release_tossCancelFails_throwsReleaseConflictAndKeepsState() {
			Payment payment = authorizedPayment();
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.of(payment));
			given(tossPaymentClient.cancel(PAYMENT_KEY, "제안 거절/취소/만료")).willReturn(false);

			assertThatThrownBy(() -> paymentService.release(OFFER_ID, LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.extracting(PaymentServiceTest::errorCodeOf)
					.isEqualTo(PaymentErrorCode.PAYMENT_409_RELEASE);
			assertThat(payment.getReleasedAt()).isNull();
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
		}

		@Test
		@DisplayName("pgTransactionId 가 없으면(mock 시절 승인) 토스를 호출하지 않고 상태만 바뀐다")
		void release_noPgTransactionId_skipsTossCallAndReleases() {
			Payment payment = Payment.authorize(null, PaymentMethod.TOSS_PAY, null, 100_000, null, LocalDateTime.now());
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.of(payment));

			paymentService.release(OFFER_ID, LocalDateTime.now());

			assertThat(payment.getReleasedAt()).isNotNull();
			verifyNoInteractions(tossPaymentClient);
		}
	}

	@Nested
	@DisplayName("결제 환불")
	class Refund {

		@Test
		@DisplayName("CAPTURED 상태면 토스 취소를 호출하고 REFUNDED 로 바뀐다")
		void refund_captured_becomesRefunded() {
			Payment payment = capturedPayment();
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.of(payment));
			given(tossPaymentClient.cancel(PAYMENT_KEY, "상품 발송 전 거래 취소")).willReturn(true);

			paymentService.refund(OFFER_ID, LocalDateTime.now());

			assertThat(payment.getRefundedAt()).isNotNull();
		}

		@Test
		@DisplayName("연결된 결제가 없으면 PAYMENT_409_REFUND 이다")
		void refund_noPayment_throwsRefundConflict() {
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> paymentService.refund(OFFER_ID, LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.extracting(PaymentServiceTest::errorCodeOf)
					.isEqualTo(PaymentErrorCode.PAYMENT_409_REFUND);
		}

		@Test
		@DisplayName("CAPTURED 가 아니면 PAYMENT_409_REFUND 이다")
		void refund_notCaptured_throwsRefundConflict() {
			Payment payment = authorizedPayment();
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.of(payment));

			assertThatThrownBy(() -> paymentService.refund(OFFER_ID, LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.extracting(PaymentServiceTest::errorCodeOf)
					.isEqualTo(PaymentErrorCode.PAYMENT_409_REFUND);
		}

		@Test
		@DisplayName("토스 취소 호출이 실패하면 상태를 바꾸지 않고 PAYMENT_409_REFUND 이다")
		void refund_tossCancelFails_throwsRefundConflictAndKeepsState() {
			Payment payment = capturedPayment();
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.of(payment));
			given(tossPaymentClient.cancel(PAYMENT_KEY, "상품 발송 전 거래 취소")).willReturn(false);

			assertThatThrownBy(() -> paymentService.refund(OFFER_ID, LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.extracting(PaymentServiceTest::errorCodeOf)
					.isEqualTo(PaymentErrorCode.PAYMENT_409_REFUND);
			assertThat(payment.getRefundedAt()).isNull();
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
		}

		@Test
		@DisplayName("pgTransactionId 가 없으면(mock 시절 승인) 토스를 호출하지 않고 상태만 바뀐다")
		void refund_noPgTransactionId_skipsTossCallAndRefunds() {
			Payment payment = Payment.authorize(null, PaymentMethod.TOSS_PAY, null, 100_000, null, LocalDateTime.now());
			payment.capture(LocalDateTime.now());
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.of(payment));

			paymentService.refund(OFFER_ID, LocalDateTime.now());

			assertThat(payment.getRefundedAt()).isNotNull();
			verifyNoInteractions(tossPaymentClient);
		}
	}

	@Nested
	@DisplayName("토스페이먼츠 결제 승인 호출")
	class ConfirmWithPg {

		@Test
		@DisplayName("토스 승인이 성공하면 응답을 그대로 돌려준다")
		void confirmWithPg_success_returnsResponse() {
			TossConfirmResponse response = new TossConfirmResponse(PAYMENT_KEY, ORDER_ID, 100_000, null);
			given(tossPaymentClient.confirm(PAYMENT_KEY, ORDER_ID, 100_000))
					.willReturn(new ConfirmOutcome.Success(response));

			TossConfirmResponse result = paymentService.confirmWithPg(PAYMENT_KEY, ORDER_ID, 100_000);

			assertThat(result).isEqualTo(response);
		}

		@Test
		@DisplayName("토스가 명시적으로 거부하면 PAYMENT_402 이다")
		void confirmWithPg_rejected_throwsPaymentRequired() {
			given(tossPaymentClient.confirm(PAYMENT_KEY, ORDER_ID, 100_000))
					.willReturn(new ConfirmOutcome.Rejected());

			assertThatThrownBy(() -> paymentService.confirmWithPg(PAYMENT_KEY, ORDER_ID, 100_000))
					.isInstanceOf(CustomException.class)
					.extracting(PaymentServiceTest::errorCodeOf)
					.isEqualTo(PaymentErrorCode.PAYMENT_402);
		}

		@Test
		@DisplayName("타임아웃·5xx 처럼 응답을 못 받으면(결과 불명) PAYMENT_402 가 아니라 PAYMENT_503_CONFIRM_UNKNOWN 이다")
		void confirmWithPg_unknown_throwsConfirmUnknownNotPaymentRequired() {
			given(tossPaymentClient.confirm(PAYMENT_KEY, ORDER_ID, 100_000))
					.willReturn(new ConfirmOutcome.Unknown());

			assertThatThrownBy(() -> paymentService.confirmWithPg(PAYMENT_KEY, ORDER_ID, 100_000))
					.isInstanceOf(CustomException.class)
					.extracting(PaymentServiceTest::errorCodeOf)
					.isEqualTo(PaymentErrorCode.PAYMENT_503_CONFIRM_UNKNOWN);
		}
	}

	@Nested
	@DisplayName("보정 환불")
	class CompensateFailedAuthorization {

		@Test
		@DisplayName("토스 취소가 실패해도 예외를 던지지 않는다 — 이미 다른 에러로 응답이 끝나는 경로다")
		void compensateFailedAuthorization_tossCancelFails_doesNotThrow() {
			given(tossPaymentClient.cancel(PAYMENT_KEY, "제안 저장 실패에 따른 환불")).willReturn(false);

			paymentService.compensateFailedAuthorization(PAYMENT_KEY);
		}
	}

	private static Payment authorizedPayment() {
		return Payment.authorize(null, PaymentMethod.TOSS_PAY, "토스페이", 100_000, PAYMENT_KEY, LocalDateTime.now());
	}

	private static Payment capturedPayment() {
		Payment payment = authorizedPayment();
		payment.capture(LocalDateTime.now());
		return payment;
	}

	private static Payment releasedPayment() {
		Payment payment = authorizedPayment();
		payment.release(LocalDateTime.now());
		return payment;
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
