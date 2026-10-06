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
 * 채팅 메시지 전송 진입점. 클라이언트는 /app/chat-rooms/{roomId}/messages 로 보냄 —
 * 검증·저장·알림·브로드캐스트는 전부 ChatService.sendMessage 가 함.
 *
 * 예외 응답은 여기 두지 않음 — WebSocketExceptionHandler 가 HTTP 쪽 GlobalExceptionHandler 와
 * 같은 자리(global/exception)에서 전역으로 처리함.
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
		// StompPrincipal.getName() 이 userId 문자열이라 바로 파싱함
		Long senderId = Long.parseLong(principal.getName());
		chatService.sendMessage(senderId, roomId, request);
	}
}
