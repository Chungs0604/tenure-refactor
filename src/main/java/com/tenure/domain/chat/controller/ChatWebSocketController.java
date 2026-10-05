package com.tenure.domain.chat.controller;

import com.tenure.domain.chat.dto.request.ChatMessageSendRequest;
import com.tenure.domain.chat.service.ChatService;
import com.tenure.global.exception.CustomException;
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
			@DestinationVariable Long roomId, @Payload ChatMessageSendRequest request, Principal principal
	) {
		Long senderId = Long.parseLong(principal.getName());
		chatService.sendMessage(senderId, roomId, request);
	}

	/** 발신자 본인에게만 에러를 돌려준다 — 상대방은 이 요청을 모르므로 알 필요가 없다 */
	@MessageExceptionHandler(CustomException.class)
	public void handleError(CustomException e, Principal principal) {
		log.info("WS 메시지 전송 실패 code={} message={}", e.getErrorCode().getCode(), e.getMessage());
		messagingTemplate.convertAndSendToUser(
				principal.getName(), "/queue/errors", e.getErrorCode().getCode() + ": " + e.getMessage());
	}
}
