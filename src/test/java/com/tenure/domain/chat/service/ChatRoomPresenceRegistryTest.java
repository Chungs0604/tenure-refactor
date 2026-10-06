package com.tenure.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 순수 in-memory 레지스트리라 Mockito 없이 실제 인스턴스로 검증한다.
 * 참조 카운트(같은 유저의 다중 기기 구독)와 세션 단위 일괄 정리가 핵심 분기다.
 */
@DisplayName("ChatRoomPresenceRegistry")
class ChatRoomPresenceRegistryTest {

	private static final Long ROOM_ID = 100L;
	private static final Long OTHER_ROOM_ID = 200L;
	private static final Long USER_ID = 1L;
	private static final Long OTHER_USER_ID = 2L;

	private final ChatRoomPresenceRegistry registry = new ChatRoomPresenceRegistry();

	@Nested
	@DisplayName("구독·구독해제")
	class SubscribeUnsubscribe {

		@Test
		@DisplayName("구독하면 그 방을 보고 있는 것으로 판정한다")
		void subscribe_thenIsViewingTrue() {
			registry.subscribe("session-1", "sub-1", ROOM_ID, USER_ID);

			assertThat(registry.isViewing(ROOM_ID, USER_ID)).isTrue();
		}

		@Test
		@DisplayName("구독한 적 없는 방·유저 조합은 보고 있지 않은 것으로 판정한다")
		void noSubscription_isViewingFalse() {
			assertThat(registry.isViewing(ROOM_ID, USER_ID)).isFalse();
		}

		@Test
		@DisplayName("구독해제하면 더 이상 보고 있지 않은 것으로 판정한다")
		void unsubscribe_thenIsViewingFalse() {
			registry.subscribe("session-1", "sub-1", ROOM_ID, USER_ID);
			registry.unsubscribe("session-1", "sub-1");

			assertThat(registry.isViewing(ROOM_ID, USER_ID)).isFalse();
		}

		@Test
		@DisplayName("존재하지 않는 구독 id 를 해제해도 예외 없이 무시한다")
		void unsubscribe_unknownSubscriptionId_noop() {
			registry.unsubscribe("session-1", "never-subscribed");

			assertThat(registry.isViewing(ROOM_ID, USER_ID)).isFalse();
		}

		@Test
		@DisplayName("같은 방이라도 다른 유저의 구독에는 영향을 주지 않는다")
		void subscribe_differentUserSameRoom_independentlyTracked() {
			registry.subscribe("session-1", "sub-1", ROOM_ID, USER_ID);

			assertThat(registry.isViewing(ROOM_ID, OTHER_USER_ID)).isFalse();
		}

		@Test
		@DisplayName("같은 유저라도 다른 방의 구독에는 영향을 주지 않는다")
		void subscribe_sameUserDifferentRoom_independentlyTracked() {
			registry.subscribe("session-1", "sub-1", ROOM_ID, USER_ID);

			assertThat(registry.isViewing(OTHER_ROOM_ID, USER_ID)).isFalse();
		}

		@Test
		@DisplayName("다른 세션이 똑같은 구독 id 문자열을 써도 서로 덮어쓰지 않는다")
		void subscribe_sameSubscriptionIdDifferentSessions_doNotCollide() {
			// STOMP 구독 id 는 "그 세션 안에서만" 고유하면 됨 — 클라이언트 라이브러리가
			// 세션마다 1부터 다시 세는 카운터로 id 를 만들면 서로 다른 유저가 "sub-1" 을
			// 동시에 쓰는 일이 실제로 생긴다. 런타임 검증 중 이 충돌로 세션-2 의 구독해제가
			// 세션-1 의 레코드를 지우는 버그가 발견됐음 — 재발 방지용 테스트.
			registry.subscribe("session-1", "sub-1", ROOM_ID, USER_ID);
			registry.subscribe("session-2", "sub-1", OTHER_ROOM_ID, OTHER_USER_ID);

			registry.unsubscribe("session-2", "sub-1");

			assertThat(registry.isViewing(ROOM_ID, USER_ID)).isTrue();
			assertThat(registry.isViewing(OTHER_ROOM_ID, OTHER_USER_ID)).isFalse();
		}

		@Test
		@DisplayName("같은 (세션, 구독id)로 UNSUBSCRIBE 없이 SUBSCRIBE 가 다시 와도 중복으로 세지 않는다")
		void subscribe_sameKeyAgainWithoutUnsubscribe_doesNotDoubleCount() {
			// 클라이언트 재시도 등으로 같은 구독이 두 번 올 수 있음 — 참조 카운트가 2로 올라가면
			// 그 다음 정상적인 UNSUBSCRIBE 한 번만으로는 안 지워져서 영원히 viewing 으로 남는다
			registry.subscribe("session-1", "sub-1", ROOM_ID, USER_ID);
			registry.subscribe("session-1", "sub-1", ROOM_ID, USER_ID);

			registry.unsubscribe("session-1", "sub-1");

			assertThat(registry.isViewing(ROOM_ID, USER_ID)).isFalse();
		}
	}

	@Nested
	@DisplayName("다중 기기 참조 카운트")
	class MultiDeviceRefCount {

		@Test
		@DisplayName("같은 유저가 두 기기로 같은 방을 구독 중이면 한 기기가 끊겨도 여전히 보고 있는 것이다")
		void twoDevicesSameRoom_oneUnsubscribes_stillViewing() {
			registry.subscribe("session-1", "sub-1", ROOM_ID, USER_ID);
			registry.subscribe("session-2", "sub-2", ROOM_ID, USER_ID);

			registry.unsubscribe("session-1", "sub-1");

			assertThat(registry.isViewing(ROOM_ID, USER_ID)).isTrue();
		}

		@Test
		@DisplayName("두 기기 모두 구독해제하면 더 이상 보고 있지 않은 것이다")
		void twoDevicesSameRoom_bothUnsubscribe_notViewing() {
			registry.subscribe("session-1", "sub-1", ROOM_ID, USER_ID);
			registry.subscribe("session-2", "sub-2", ROOM_ID, USER_ID);

			registry.unsubscribe("session-1", "sub-1");
			registry.unsubscribe("session-2", "sub-2");

			assertThat(registry.isViewing(ROOM_ID, USER_ID)).isFalse();
		}
	}

	@Nested
	@DisplayName("연결 종료")
	class Disconnect {

		@Test
		@DisplayName("세션이 끊기면 그 세션이 구독했던 방은 더 이상 보고 있지 않은 것이다")
		void disconnect_removesAllSubscriptionsOfThatSession() {
			registry.subscribe("session-1", "sub-1", ROOM_ID, USER_ID);
			registry.subscribe("session-1", "sub-2", OTHER_ROOM_ID, USER_ID);

			registry.disconnect("session-1");

			assertThat(registry.isViewing(ROOM_ID, USER_ID)).isFalse();
			assertThat(registry.isViewing(OTHER_ROOM_ID, USER_ID)).isFalse();
		}

		@Test
		@DisplayName("세션이 끊겨도 다른 세션(다른 기기)의 구독은 그대로 유지된다")
		void disconnect_otherSessionSubscriptionUnaffected() {
			registry.subscribe("session-1", "sub-1", ROOM_ID, USER_ID);
			registry.subscribe("session-2", "sub-2", ROOM_ID, USER_ID);

			registry.disconnect("session-1");

			assertThat(registry.isViewing(ROOM_ID, USER_ID)).isTrue();
		}

		@Test
		@DisplayName("존재하지 않는 세션을 연결 종료해도 예외 없이 무시한다")
		void disconnect_unknownSessionId_noop() {
			registry.disconnect("never-connected");

			assertThat(registry.isViewing(ROOM_ID, USER_ID)).isFalse();
		}

		@Test
		@DisplayName("연결이 끊긴 세션의 구독 id 로 다시 구독해제해도 예외 없이 무시한다")
		void unsubscribe_afterDisconnect_noop() {
			registry.subscribe("session-1", "sub-1", ROOM_ID, USER_ID);
			registry.disconnect("session-1");

			registry.unsubscribe("session-1", "sub-1");

			assertThat(registry.isViewing(ROOM_ID, USER_ID)).isFalse();
		}

		@Test
		@DisplayName("세션이 끊겨도 다른 세션이 쓰던 같은 구독 id 문자열은 영향받지 않는다")
		void disconnect_sameSubscriptionIdOtherSession_unaffected() {
			registry.subscribe("session-1", "sub-1", ROOM_ID, USER_ID);
			registry.subscribe("session-2", "sub-1", OTHER_ROOM_ID, OTHER_USER_ID);

			registry.disconnect("session-1");

			assertThat(registry.isViewing(ROOM_ID, USER_ID)).isFalse();
			assertThat(registry.isViewing(OTHER_ROOM_ID, OTHER_USER_ID)).isTrue();
		}
	}
}
