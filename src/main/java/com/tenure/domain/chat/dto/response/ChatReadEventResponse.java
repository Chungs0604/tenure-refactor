package com.tenure.domain.chat.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** WS 전용 — 읽음 처리를 상대방에게 실시간으로 알리는 이벤트. REST 응답에는 쓰이지 않음 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatReadEventResponse {

	private final Long roomId;

	private final Long lastReadMessageId;
}
