package com.tenure.domain.chat.dto.response;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** WS 전용 — 채팅방 목록 화면이 구독 중일 때 한 행을 실시간으로 갱신시키는 이벤트. REST 응답에는 쓰이지 않음 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatRoomListUpdateResponse {

	private final Long roomId;

	private final String lastMessage;

	private final LocalDateTime lastMessageAt;

	private final Integer unreadCount;
}
