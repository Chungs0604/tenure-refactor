package com.tenure.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * HTTP 핸드셰이크가 permitAll 이라 실제 인증 경계는 여기 CONNECT 프레임이다 — WithdrawnUserStore
 * 를 JwtAuthenticationFilter 와 함께 고치지 않으면 채팅 쪽에만 구멍이 남는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StompAuthChannelInterceptor")
class StompAuthChannelInterceptorTest {

	private static final Long USER_ID = 1L;
	private static final String TOKEN = "access-token";

	@Mock private JwtTokenProvider tokenProvider;
	@Mock private WithdrawnUserStore withdrawnUserStore;
	@Mock private Claims claims;

	private StompAuthChannelInterceptor interceptor;

	@BeforeEach
	void setUp() {
		interceptor = new StompAuthChannelInterceptor(tokenProvider, withdrawnUserStore);
	}

	@Test
	@DisplayName("탈퇴하지 않은 사용자면 CONNECT 에 principal 을 세팅한다")
	void notWithdrawn_setsPrincipalOnConnect() {
		given(tokenProvider.parseOrNull(TOKEN)).willReturn(claims);
		given(tokenProvider.getTokenId(any())).willReturn(null);
		given(tokenProvider.getUserId(any())).willReturn(USER_ID);
		given(withdrawnUserStore.isWithdrawn(USER_ID)).willReturn(false);

		Message<?> result = interceptor.preSend(connectMessage(), null);

		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
		assertThat(accessor.getUser()).isEqualTo(new StompPrincipal(USER_ID));
	}

	@Test
	@DisplayName("탈퇴한 사용자면 서명·만료가 멀쩡해도 CONNECT 를 거부한다")
	void withdrawn_rejectsConnect() {
		given(tokenProvider.parseOrNull(TOKEN)).willReturn(claims);
		given(tokenProvider.getTokenId(any())).willReturn(null);
		given(tokenProvider.getUserId(any())).willReturn(USER_ID);
		given(withdrawnUserStore.isWithdrawn(USER_ID)).willReturn(true);

		assertThatThrownBy(() -> interceptor.preSend(connectMessage(), null))
				.isInstanceOf(MessagingException.class);
	}

	private static Message<?> connectMessage() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.setNativeHeader("Authorization", "Bearer " + TOKEN);
		// interceptor.preSend() 가 같은 메시지에 accessor.setUser() 로 principal 을 더 써넣으므로
		// 헤더를 미리 불변으로 굳히면 안 됨
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}
}
