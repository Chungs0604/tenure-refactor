package com.tenure.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.tenure.global.security.jwt.StompPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatRoomPresenceListener")
class ChatRoomPresenceListenerTest {

	private static final Long ROOM_ID = 100L;
	private static final Long USER_ID = 1L;
	private static final String SESSION_ID = "session-1";
	private static final String SUBSCRIPTION_ID = "sub-1";

	@Mock private ChatRoomPresenceRegistry presenceRegistry;
	@Mock private ChatService chatService;

	@InjectMocks private ChatRoomPresenceListener listener;

	@Nested
	@DisplayName("구독")
	class OnSubscribe {

		@Test
		@DisplayName("방 큐(/user/queue/chat-rooms/{id}) 구독이면 presence 를 등록하고 백로그를 읽음 처리한다")
		void roomQueue_registersPresenceAndMarksBacklogRead() {
			SessionSubscribeEvent event = subscribeEvent("/user/queue/chat-rooms/" + ROOM_ID, USER_ID);

			listener.onSubscribe(event);

			verify(presenceRegistry).subscribe(SESSION_ID, SUBSCRIPTION_ID, ROOM_ID, USER_ID);
			verify(chatService).markBacklogRead(USER_ID, ROOM_ID);
		}

		@Test
		@DisplayName("읽음 큐(/user/queue/chat-rooms/{id}/read) 구독은 매칭되지 않는다")
		void readQueue_doesNotMatch() {
			SessionSubscribeEvent event = subscribeEvent("/user/queue/chat-rooms/" + ROOM_ID + "/read", USER_ID);

			listener.onSubscribe(event);

			verifyNoInteractions(presenceRegistry, chatService);
		}

		@Test
		@DisplayName("목록 큐(/user/queue/chat-rooms-list) 구독은 매칭되지 않는다")
		void listQueue_doesNotMatch() {
			SessionSubscribeEvent event = subscribeEvent("/user/queue/chat-rooms-list", USER_ID);

			listener.onSubscribe(event);

			verifyNoInteractions(presenceRegistry, chatService);
		}

		@Test
		@DisplayName("destination 이 없으면 아무 것도 하지 않는다")
		void noDestination_doesNothing() {
			StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
			accessor.setSessionId(SESSION_ID);
			accessor.setSubscriptionId(SUBSCRIPTION_ID);
			Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
			SessionSubscribeEvent event = new SessionSubscribeEvent(this, message, new StompPrincipal(USER_ID));

			listener.onSubscribe(event);

			verifyNoInteractions(presenceRegistry, chatService);
		}

		@Test
		@DisplayName("인증 주체가 없으면 매칭되는 destination 이어도 아무 것도 하지 않는다")
		void noPrincipal_doesNothing() {
			StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
			accessor.setSessionId(SESSION_ID);
			accessor.setSubscriptionId(SUBSCRIPTION_ID);
			accessor.setDestination("/user/queue/chat-rooms/" + ROOM_ID);
			Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
			SessionSubscribeEvent event = new SessionSubscribeEvent(this, message);

			listener.onSubscribe(event);

			verifyNoInteractions(presenceRegistry, chatService);
		}

		@Test
		@DisplayName("백로그 읽음 처리 중 예외가 나도 리스너 밖으로 던지지 않는다")
		void markBacklogReadThrows_doesNotPropagate() {
			SessionSubscribeEvent event = subscribeEvent("/user/queue/chat-rooms/" + ROOM_ID, USER_ID);
			willThrow(new RuntimeException("읽음 처리 실패")).given(chatService).markBacklogRead(USER_ID, ROOM_ID);

			assertThatCode(() -> listener.onSubscribe(event)).doesNotThrowAnyException();

			verify(presenceRegistry).subscribe(SESSION_ID, SUBSCRIPTION_ID, ROOM_ID, USER_ID);
		}
	}

	@Nested
	@DisplayName("구독해제")
	class OnUnsubscribe {

		@Test
		@DisplayName("레지스트리의 unsubscribe 로 그대로 위임한다")
		void delegatesToRegistry() {
			StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
			accessor.setSessionId(SESSION_ID);
			accessor.setSubscriptionId(SUBSCRIPTION_ID);
			Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
			SessionUnsubscribeEvent event = new SessionUnsubscribeEvent(this, message, new StompPrincipal(USER_ID));

			listener.onUnsubscribe(event);

			verify(presenceRegistry).unsubscribe(SESSION_ID, SUBSCRIPTION_ID);
		}
	}

	@Nested
	@DisplayName("연결 종료")
	class OnDisconnect {

		@Test
		@DisplayName("레지스트리의 disconnect 로 그대로 위임한다")
		void delegatesToRegistry() {
			StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
			accessor.setSessionId(SESSION_ID);
			Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
			SessionDisconnectEvent event =
					new SessionDisconnectEvent(this, message, SESSION_ID, CloseStatus.NORMAL, new StompPrincipal(USER_ID));

			listener.onDisconnect(event);

			verify(presenceRegistry).disconnect(SESSION_ID);
			verify(presenceRegistry, never()).unsubscribe(eq(SESSION_ID), eq(SUBSCRIPTION_ID));
		}
	}

	private SessionSubscribeEvent subscribeEvent(String destination, Long userId) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setSessionId(SESSION_ID);
		accessor.setSubscriptionId(SUBSCRIPTION_ID);
		accessor.setDestination(destination);
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
		return new SessionSubscribeEvent(this, message, new StompPrincipal(userId));
	}
}
