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
	// 타임아웃·5xx 처럼 PG가 명시적으로 거부하지 않았는데 응답을 못 받은 경우 — "실패"로 단정하지
	// 않고 별도 코드로 구분한다. 돈은 빠졌을 수 있으니 확정 실패(PAYMENT_402)와 섞으면 안 된다
	PAYMENT_503_CONFIRM_UNKNOWN(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_503_CONFIRM_UNKNOWN",
			"결제 처리 상태를 확인할 수 없습니다. 잠시 후 다시 시도하거나 고객센터로 문의해주세요."),
	PAYMENT_409_CAPTURE(HttpStatus.CONFLICT, "PAYMENT_409_CAPTURE", "결제 확정에 실패했습니다."),
	PAYMENT_409_RELEASE(HttpStatus.CONFLICT, "PAYMENT_409_RELEASE", "결제 승인 해제에 실패했습니다."),
	PAYMENT_409_REFUND(HttpStatus.CONFLICT, "PAYMENT_409_REFUND", "환불 처리에 실패했습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
