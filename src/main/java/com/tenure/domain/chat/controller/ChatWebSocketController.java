package com.tenure.domain.chat.controller;

import com.tenure.domain.chat.dto.request.ChatMessageSendRequest;
import com.tenure.domain.chat.service.ChatService;
import com.tenure.global.exception.CustomException;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * 채팅 메시지 전송 진입점(API-184 대체). 클라이언트는 {@code /app/chat-rooms/{roomId}/messages}
 * 로 보낸다 — 검증·저장·알림·브로드캐스트는 전부 {@link ChatService#sendMessage} 가 한다.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

	private final ChatService chatService;
	private final SimpMessagingTemplate messagingTemplate;

	@MessageMapping("/chat-rooms/{roomId}/messages")
	public void sendMessage(
			@DestinationVariable Long roomId,
			@Valid @Payload ChatMessageSendRequest request,
			Principal principal
	) {
		Long senderId = Long.parseLong(principal.getName());
		chatService.sendMessage(senderId, roomId, request);
	}

	/** 발신자 본인에게만 에러를 돌려준다 — 상대방은 이 요청을 모르므로 알 필요가 없다 */
	@MessageExceptionHandler(CustomException.class)
	public void handleError(CustomException e, Principal principal) {
		log.info("WS 메시지 전송 실패 code={} message={}", e.getErrorCode().getCode(), e.getMessage());
		sendErrorIfAuthenticated(principal, e.getErrorCode().getCode() + ": " + e.getMessage());
	}

	/**
	 * CustomException 이 아닌 나머지(검증 실패·역직렬화 오류 등) — 잡아 두지 않으면 조용히
	 * 사라져서 클라이언트가 전송 실패를 알 길이 없다. StompAuthChannelInterceptor 가 CONNECT
	 * 없는 프레임을 막아 principal 이 null 인 경우는 정상적으로는 여기 도달하지 않지만,
	 * 방어적으로 한 번 더 확인한다.
	 */
	@MessageExceptionHandler(Exception.class)
	public void handleUnexpected(Exception e, Principal principal) {
		log.warn("WS 메시지 처리 중 예상치 못한 예외", e);
		sendErrorIfAuthenticated(principal, "COMMON_400: 요청을 처리할 수 없습니다.");
	}

	private void sendErrorIfAuthenticated(Principal principal, String payload) {
		if (principal == null) {
			return;
		}
		messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/errors", payload);
	}
}
