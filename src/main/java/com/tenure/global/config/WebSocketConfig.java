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
 * 인증은 HTTP 핸드셰이크가 아니라 CONNECT 프레임에서 함(StompAuthChannelInterceptor) —
 * 그래서 핸드셰이크 엔드포인트 자체는 SecurityConfig 에서 permitAll 임.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// 기본값(동일 출처만 허용)을 그대로 둠 — 이 서비스는 앱 화면 기준이라 브라우저 Origin
		// 검사 대상인 웹 클라이언트가 없음. 웹 클라이언트가 생기면 그때 setAllowedOriginPatterns 로
		// 허용 출처를 명시함(REST 쪽 CORS 설정과 같은 목록이어야 함)
		registry.addEndpoint("/ws-chat");
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		// /topic 은 쓰지 않음 — 채팅은 전부 1:1 이라 방 구독이 아니라 유저 큐로 보냄
		registry.enableSimpleBroker("/queue");
		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(stompAuthChannelInterceptor);
	}
}
