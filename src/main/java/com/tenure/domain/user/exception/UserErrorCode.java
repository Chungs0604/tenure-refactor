package com.tenure.domain.user.exception;

import com.tenure.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

	USER_404(HttpStatus.NOT_FOUND, "USER_404", "사용자를 찾을 수 없습니다."),
	USER_409_EMAIL(HttpStatus.CONFLICT, "USER_409_EMAIL", "이미 존재하는 이메일입니다."),
	USER_409_NICKNAME(HttpStatus.CONFLICT, "USER_409_NICKNAME", "중복된 닉네임입니다."),
	USER_400_PASSWORD(HttpStatus.BAD_REQUEST, "USER_400_PASSWORD", "비밀번호는 8자 이상이어야 합니다."),
	USER_400_AGREEMENT(HttpStatus.BAD_REQUEST, "USER_400_AGREEMENT", "필수 약관에 모두 동의해야 합니다."),
	USER_409_WITHDRAWN(HttpStatus.CONFLICT, "USER_409_WITHDRAWN", "이미 탈퇴한 계정입니다."),
	USER_409_ACTIVE_TRADE(HttpStatus.CONFLICT, "USER_409_ACTIVE_TRADE", "진행 중인 거래가 있어 탈퇴할 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
