package com.tenure.domain.payout.exception;

import com.tenure.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum PayoutErrorCode implements ErrorCode {

	PAYOUT_404(HttpStatus.NOT_FOUND, "PAYOUT_404", "정산 계좌를 찾을 수 없습니다."),
	PAYOUT_403(HttpStatus.FORBIDDEN, "PAYOUT_403", "본인의 정산 계좌가 아닙니다."),
	PAYOUT_409_PENDING_SETTLEMENT(HttpStatus.CONFLICT, "PAYOUT_409_PENDING_SETTLEMENT", "정산 대기 중인 거래가 있어 삭제할 수 없습니다."),
	PAYOUT_400_REQUIRED(HttpStatus.BAD_REQUEST, "PAYOUT_400_REQUIRED", "판매 전환을 위해 정산 계좌를 먼저 등록해주세요.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
