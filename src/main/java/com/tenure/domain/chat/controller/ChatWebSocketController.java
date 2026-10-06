package com.tenure.domain.chat.controller;

import com.tenure.domain.chat.dto.request.ChatMessageSendRequest;
import com.tenure.domain.chat.service.ChatService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * 채팅 메시지 전송 진입점(API-184 대체). 클라이언트는 {@code /app/chat-rooms/{roomId}/messages}
 * 로 보낸다 — 검증·저장·알림·브로드캐스트는 전부 {@link ChatService#sendMessage} 가 한다.
 *
 * <p>예외 응답은 여기 두지 않는다 — {@code global/exception/WebSocketExceptionHandler} 가
 * HTTP 쪽 {@code GlobalExceptionHandler} 와 같은 자리에서 전역으로 처리한다.
 */
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

	private final ChatService chatService;

	@MessageMapping("/chat-rooms/{roomId}/messages")
	public void sendMessage(
			@DestinationVariable Long roomId,
			@Valid @Payload ChatMessageSendRequest request,
			Principal principal
	) {
		Long senderId = Long.parseLong(principal.getName());
		chatService.sendMessage(senderId, roomId, request);
	}
}
