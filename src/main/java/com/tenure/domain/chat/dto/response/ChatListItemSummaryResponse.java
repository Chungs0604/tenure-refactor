package com.tenure.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "채팅방 목록 행의 아이템 요약 — 목록엔 썸네일이 없어 브랜드·이름만 내려줌")
public class ChatListItemSummaryResponse {

	@Schema(description = "브랜드명", example = "Levis / LVC 1955 501")
	private final String brandName;

	@Schema(description = "아이템명")
	private final String name;
}
