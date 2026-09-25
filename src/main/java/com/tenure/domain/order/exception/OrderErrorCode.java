package com.tenure.domain.order.exception;

import com.tenure.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

	ORDER_404(HttpStatus.NOT_FOUND, "ORDER_404", "거래를 찾을 수 없습니다."),
	ORDER_403(HttpStatus.FORBIDDEN, "ORDER_403", "본인과 관련된 거래가 아닙니다."),
	ORDER_409_STATUS(HttpStatus.CONFLICT, "ORDER_409_STATUS", "현재 단계에서 수행할 수 없는 요청입니다."),
	ORDER_409_CANCEL_WINDOW(HttpStatus.CONFLICT, "ORDER_409_CANCEL_WINDOW", "상품 발송 이후에는 거래를 취소할 수 없습니다."),
	ORDER_400_TRACKING(HttpStatus.BAD_REQUEST, "ORDER_400_TRACKING", "운송장 번호 형식이 올바르지 않습니다."),
	ORDER_404_TRACKING(HttpStatus.NOT_FOUND, "ORDER_404_TRACKING", "주문 정보를 찾을 수 없어요.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
