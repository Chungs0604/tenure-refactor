package com.tenure.domain.block.exception;

import com.tenure.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum BlockErrorCode implements ErrorCode {

	BLOCK_409(HttpStatus.CONFLICT, "BLOCK_409", "이미 차단한 사용자입니다."),
	BLOCK_400_SELF(HttpStatus.BAD_REQUEST, "BLOCK_400_SELF", "자기 자신을 차단할 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
