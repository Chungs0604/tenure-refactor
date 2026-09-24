package com.tenure.global.exception;

import lombok.Getter;

/**
 * 비즈니스 예외. 이 프로젝트의 예외는 원칙적으로 이 하나다.
 *
 * <p>새 예외 클래스는 (1) 재시도 대상을 타입으로 구분할 때 (2) 롤백 규칙을 분기할 때
 * (3) 프레임워크가 타입 계층을 요구할 때만 만들고, 만들 때는 이유를 주석으로 남긴다.
 */
@Getter
public class CustomException extends RuntimeException {

	private final transient ErrorCode errorCode;

	public CustomException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}
}
