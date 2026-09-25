package com.tenure.domain.address.exception;

import com.tenure.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum AddressErrorCode implements ErrorCode {

	ADDRESS_404(HttpStatus.NOT_FOUND, "ADDRESS_404", "배송지를 찾을 수 없습니다."),
	ADDRESS_403(HttpStatus.FORBIDDEN, "ADDRESS_403", "본인의 배송지가 아닙니다."),
	ADDRESS_409_LAST_DEFAULT(HttpStatus.CONFLICT, "ADDRESS_409_LAST_DEFAULT", "기본 배송지는 삭제할 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
