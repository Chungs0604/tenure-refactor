package com.tenure.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "채팅방 목록 행")
public class ChatRoomSummaryResponse {

	@Schema(description = "채팅방 id", example = "1")
	private final Long roomId;

	@Schema(description = "상대")
	private final ChatCounterpartResponse counterpart;

	@Schema(description = "마지막 메시지 미리보기. 메시지가 아직 없으면 null")
	private final String lastMessage;

	@Schema(description = "마지막 메시지 시각. 메시지가 아직 없으면 방 개설 시각", example = "2026-06-21T18:30:00")
	private final LocalDateTime lastMessageAt;

	@Schema(description = "안읽음 수", example = "1")
	private final Integer unreadCount;

	@Schema(description = "아이템 요약")
	private final ChatListItemSummaryResponse item;

	@Schema(description = "최근 착용일. 인증이 없으면 null", example = "2026-06-21")
	private final LocalDate recentWornDate;
}
