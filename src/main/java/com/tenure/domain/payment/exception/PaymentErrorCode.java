package com.tenure.domain.payment.exception;

import com.tenure.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

	PAYMENT_402(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_402", "결제 승인에 실패했습니다."),
	PAYMENT_409_CAPTURE(HttpStatus.CONFLICT, "PAYMENT_409_CAPTURE", "결제 확정에 실패했습니다."),
	PAYMENT_409_REFUND(HttpStatus.CONFLICT, "PAYMENT_409_REFUND", "환불 처리에 실패했습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
