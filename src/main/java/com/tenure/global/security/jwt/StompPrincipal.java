package com.tenure.global.security.jwt;

import java.security.Principal;

/**
 * STOMP 세션의 인증 주체. {@code convertAndSendToUser} 가 이 {@code getName()} 으로
 * 수신자 세션을 찾으므로, 보낼 때 쓰는 문자열(userId)과 반드시 같은 형식이어야 한다.
 */
public record StompPrincipal(Long userId) implements Principal {

	@Override
	public String getName() {
		return userId.toString();
	}
}
