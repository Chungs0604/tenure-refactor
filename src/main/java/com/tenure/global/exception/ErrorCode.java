package com.tenure.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드 계약. 공통은 {@link CommonErrorCode}, 도메인별은
 * {@code domain.<도메인>.exception.{도메인}ErrorCode} 에 둔다.
 *
 * <p>HTTP 상태는 이 인터페이스가 결정한다 — 컨트롤러나 핸들러가 따로 판단하지 않는다.
 */
public interface ErrorCode {

	String getCode();

	String getMessage();

	HttpStatus getStatus();
}
