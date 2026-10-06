package com.tenure.domain.chat.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * 방 메시지 큐(/user/queue/chat-rooms/{roomId}) 구독·구독해제·연결종료를 받아 presence 를 갱신함.
 *
 * 구독 시점은 "방에 들어옴"으로 보고 그 방의 안읽은 백로그를 바로 읽음 처리함. 요청·응답이 있는
 * 흐름이 아니라 실패해도 던질 곳이 없어 여기서 전부 잡고 로그만 남김 — presence 갱신 자체를
 * 막을 이유는 아님.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomPresenceListener {

	// 목록 큐(chat-rooms-list)·읍음 큐(chat-rooms/{id}/read)는 매칭에서 빠짐 — 방 자체를 보는
	// 큐만 presence 대상임
	private static final Pattern ROOM_QUEUE = Pattern.compile("^/user/queue/chat-rooms/(\\d+)$");

	private final ChatRoomPresenceRegistry presenceRegistry;
	private final ChatService chatService;

	@EventListener
	public void onSubscribe(SessionSubscribeEvent event) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
		Long roomId = roomIdOf(accessor.getDestination());
		if (roomId == null || event.getUser() == null) {
			return;
		}

		Long userId = Long.parseLong(event.getUser().getName());
		presenceRegistry.subscribe(accessor.getSessionId(), accessor.getSubscriptionId(), roomId, userId);

		try {
			chatService.markBacklogRead(userId, roomId);
		} catch (Exception e) {
			log.warn("구독 시점 읽음 처리 실패 roomId={} userId={}", roomId, userId, e);
		}
	}

	@EventListener
	public void onUnsubscribe(SessionUnsubscribeEvent event) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
		presenceRegistry.unsubscribe(accessor.getSessionId(), accessor.getSubscriptionId());
	}

	@EventListener
	public void onDisconnect(SessionDisconnectEvent event) {
		presenceRegistry.disconnect(event.getSessionId());
	}

	private Long roomIdOf(String destination) {
		if (destination == null) {
			return null;
		}
		Matcher matcher = ROOM_QUEUE.matcher(destination);
		return matcher.matches() ? Long.parseLong(matcher.group(1)) : null;
	}
}
