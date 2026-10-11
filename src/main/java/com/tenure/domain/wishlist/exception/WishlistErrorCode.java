package com.tenure.domain.wishlist.exception;

import com.tenure.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum WishlistErrorCode implements ErrorCode {

	WISHLIST_409(HttpStatus.CONFLICT, "WISHLIST_409", "이미 관심 등록한 아이템입니다."),
	WISHLIST_404(HttpStatus.NOT_FOUND, "WISHLIST_404", "위시리스트 항목을 찾을 수 없습니다."),
	WISHLIST_403(HttpStatus.FORBIDDEN, "WISHLIST_403", "본인의 위시리스트 항목이 아닙니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
