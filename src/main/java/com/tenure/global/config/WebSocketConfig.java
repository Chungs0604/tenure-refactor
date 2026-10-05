package com.tenure.global.config;

import com.tenure.global.security.jwt.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 채팅 실시간 송수신 STOMP 설정.
 *
 * <p>인증은 HTTP 핸드셰이크가 아니라 CONNECT 프레임에서 한다({@link StompAuthChannelInterceptor}) —
 * 그래서 핸드셰이크 엔드포인트 자체는 SecurityConfig 에서 permitAll 이다.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws-chat");
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		// /topic 은 쓰지 않는다 — 채팅은 전부 1:1 이라 방 구독이 아니라 유저 큐로 보낸다
		registry.enableSimpleBroker("/queue");
		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(stompAuthChannelInterceptor);
	}
}
