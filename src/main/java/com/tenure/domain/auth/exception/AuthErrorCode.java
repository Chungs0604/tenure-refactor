package com.tenure.domain.auth.exception;

import com.tenure.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

	AUTH_401_CREDENTIAL(HttpStatus.UNAUTHORIZED, "AUTH_401_CREDENTIAL", "이메일 또는 비밀번호가 올바르지 않습니다."),
	AUTH_404_EMAIL(HttpStatus.NOT_FOUND, "AUTH_404_EMAIL", "가입되지 않은 이메일입니다."),
	AUTH_401_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_TOKEN", "유효하지 않은 토큰입니다."),
	AUTH_400_CODE(HttpStatus.BAD_REQUEST, "AUTH_400_CODE", "인증번호가 틀렸습니다."),
	AUTH_410_CODE(HttpStatus.GONE, "AUTH_410_CODE", "인증번호가 만료되었습니다."),
	AUTH_400_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "AUTH_400_NOT_VERIFIED", "이메일 인증이 완료되지 않았습니다."),
	AUTH_401_VERIFICATION_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_VERIFICATION_TOKEN", "유효하지 않은 인증 토큰입니다."),
	AUTH_410_VERIFICATION_TOKEN(HttpStatus.GONE, "AUTH_410_VERIFICATION_TOKEN", "인증 토큰이 만료되었습니다."),
	AUTH_409_TOKEN_CONSUMED(HttpStatus.CONFLICT, "AUTH_409_TOKEN_CONSUMED", "이미 사용된 인증 토큰입니다."),
	AUTH_400_PURPOSE_MISMATCH(HttpStatus.BAD_REQUEST, "AUTH_400_PURPOSE_MISMATCH", "용도가 맞지 않는 인증 토큰입니다."),
	AUTH_429_CODE_RESEND_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "AUTH_429_CODE_RESEND_COOLDOWN", "잠시 후 다시 시도해주세요."),
	AUTH_429_CODE_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "AUTH_429_CODE_DAILY_LIMIT", "오늘은 더 이상 인증번호를 받을 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
