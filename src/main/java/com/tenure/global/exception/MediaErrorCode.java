package com.tenure.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum MediaErrorCode implements ErrorCode {

	MEDIA_400_TYPE(HttpStatus.BAD_REQUEST, "MEDIA_400_TYPE", "지원하지 않는 이미지 형식입니다."),
	MEDIA_413(HttpStatus.PAYLOAD_TOO_LARGE, "MEDIA_413", "이미지 용량이 너무 큽니다."),
	MEDIA_500_STORAGE(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_500_STORAGE", "이미지 저장에 실패했습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
