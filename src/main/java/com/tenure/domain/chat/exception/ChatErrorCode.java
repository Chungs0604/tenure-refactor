package com.tenure.domain.chat.exception;

import com.tenure.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements ErrorCode {

	CHAT_404_ROOM(HttpStatus.NOT_FOUND, "CHAT_404_ROOM", "채팅방을 찾을 수 없습니다."),
	CHAT_403(HttpStatus.FORBIDDEN, "CHAT_403", "참여 중인 채팅방이 아닙니다."),
	CHAT_400_NOT_OPENABLE(HttpStatus.BAD_REQUEST, "CHAT_400_NOT_OPENABLE", "구매 제안을 받지 않는 미판매 상품은 채팅을 시작할 수 없습니다."),
	CHAT_403_BLOCKED(HttpStatus.FORBIDDEN, "CHAT_403_BLOCKED", "차단 관계라 메시지를 보낼 수 없습니다."),
	CHAT_400_SELF(HttpStatus.BAD_REQUEST, "CHAT_400_SELF", "본인의 상품에는 채팅방을 열 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
