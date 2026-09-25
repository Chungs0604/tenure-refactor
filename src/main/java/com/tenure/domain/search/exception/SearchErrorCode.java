package com.tenure.domain.search.exception;

import com.tenure.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum SearchErrorCode implements ErrorCode {

	SEARCH_404_HISTORY(HttpStatus.NOT_FOUND, "SEARCH_404_HISTORY", "검색 기록을 찾을 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
