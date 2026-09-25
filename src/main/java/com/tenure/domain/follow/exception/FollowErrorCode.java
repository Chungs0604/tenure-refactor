package com.tenure.domain.follow.exception;

import com.tenure.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum FollowErrorCode implements ErrorCode {

	FOLLOW_409(HttpStatus.CONFLICT, "FOLLOW_409", "이미 팔로우한 사용자입니다."),
	FOLLOW_400_SELF(HttpStatus.BAD_REQUEST, "FOLLOW_400_SELF", "자기 자신을 팔로우할 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
