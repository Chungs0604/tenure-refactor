package com.tenure.domain.payment.service;

import com.tenure.domain.payment.entity.Payment;
import com.tenure.domain.payment.enums.PaymentMethod;
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

/**
 * capture·release·refund 가 저마다 다른 409 를 쓰는지가 핵심 — 못 찾았을 때·상태가 안 맞을 때
 * 모두 호출부 맥락에 맞는 ErrorCode 여야 한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

	private static final Long OFFER_ID = 1L;

	@Mock private PaymentRepository paymentRepository;

	@InjectMocks private PaymentService paymentService;

	@Nested
	@DisplayName("결제 확정")
	class Capture {

		@Test
		@DisplayName("AUTHORIZED 상태면 CAPTURED 로 바뀐다")
		void capture_authorized_becomesCaptured() {
			Payment payment = authorizedPayment();
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.of(payment));

			paymentService.capture(OFFER_ID, LocalDateTime.now());

			assertThat(payment.getCapturedAt()).isNotNull();
		}

		@Test
		@DisplayName("연결된 결제가 없으면 PAYMENT_409_CAPTURE 이다")
		void capture_noPayment_throwsCaptureConflict() {
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> paymentService.capture(OFFER_ID, LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.extracting(PaymentServiceTest::errorCodeOf)
					.isEqualTo(PaymentErrorCode.PAYMENT_409_CAPTURE);
		}

		@Test
		@DisplayName("AUTHORIZED 가 아니면 PAYMENT_409_CAPTURE 이다")
		void capture_notAuthorized_throwsCaptureConflict() {
			Payment payment = releasedPayment();
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.of(payment));

			assertThatThrownBy(() -> paymentService.capture(OFFER_ID, LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.extracting(PaymentServiceTest::errorCodeOf)
					.isEqualTo(PaymentErrorCode.PAYMENT_409_CAPTURE);
		}
	}

	@Nested
	@DisplayName("결제 승인 해제")
	class Release {

		@Test
		@DisplayName("AUTHORIZED 상태면 RELEASED 로 바뀐다")
		void release_authorized_becomesReleased() {
			Payment payment = authorizedPayment();
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.of(payment));

			paymentService.release(OFFER_ID, LocalDateTime.now());

			assertThat(payment.getReleasedAt()).isNotNull();
		}

		@Test
		@DisplayName("이미 RELEASED 면 멱등하게 끝난다")
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
	}

	@Nested
	@DisplayName("결제 환불")
	class Refund {

		@Test
		@DisplayName("CAPTURED 상태면 REFUNDED 로 바뀐다")
		void refund_captured_becomesRefunded() {
			Payment payment = capturedPayment();
			given(paymentRepository.findByOfferIdForUpdate(OFFER_ID)).willReturn(Optional.of(payment));

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
	}

	private static Payment authorizedPayment() {
		return Payment.authorize(null, PaymentMethod.TOSS_PAY, 100_000, LocalDateTime.now());
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
