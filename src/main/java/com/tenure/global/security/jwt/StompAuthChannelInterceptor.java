package com.tenure.global.security.jwt;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * STOMP CONNECT 프레임의 {@code Authorization} 네이티브 헤더로 인증한다.
 *
 * <p>HTTP 핸드셰이크 자체는 permitAll 이다(SecurityConfig) — 실제 인증 경계는 여기,
 * CONNECT 프레임이다. 기존 {@link JwtAuthenticationFilter} 와 같은 토큰·판정 기준을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

	private static final String HEADER = "Authorization";
	private static final String PREFIX = "Bearer ";

	private final JwtTokenProvider tokenProvider;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null) {
			return message;
		}

		if (StompCommand.CONNECT.equals(accessor.getCommand())) {
			accessor.setUser(authenticate(accessor));
			return message;
		}

		// CONNECT 없이 곧바로 SEND·SUBSCRIBE 가 오면 principal 이 비어 핸들러에서 NPE 로 샌다 —
		// 핸드셰이크가 permitAll 이라 여기서 막는 게 유일한 경계다. CONNECT 자체는 위에서 처리했다
		if (accessor.getCommand() != null && accessor.getUser() == null) {
			throw new MessagingException("인증되지 않은 연결입니다.");
		}

		return message;
	}

	private StompPrincipal authenticate(StompHeaderAccessor accessor) {
		String token = resolveToken(accessor);
		Claims claims = token != null ? tokenProvider.parseOrNull(token) : null;

		// refresh token 은 인증에 쓰지 않는다 — tid 클레임이 있으면 refresh 다 (JwtAuthenticationFilter 와 동일 기준)
		if (claims == null || tokenProvider.getTokenId(claims) != null) {
			throw new MessagingException("인증되지 않은 연결입니다.");
		}

		return new StompPrincipal(tokenProvider.getUserId(claims));
	}

	private String resolveToken(StompHeaderAccessor accessor) {
		String header = accessor.getFirstNativeHeader(HEADER);
		return StringUtils.hasText(header) && header.startsWith(PREFIX) ? header.substring(PREFIX.length()) : null;
	}
}
