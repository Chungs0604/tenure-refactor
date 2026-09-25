package com.tenure.domain.category.exception;

import com.tenure.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum CategoryErrorCode implements ErrorCode {

	CATEGORY_404(HttpStatus.NOT_FOUND, "CATEGORY_404", "카테고리를 찾을 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
