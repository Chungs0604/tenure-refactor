package com.tenure.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "채팅방의 아이템 요약")
public class ChatItemSummaryResponse {

	@Schema(description = "브랜드명", example = "Levis / LVC 1955 501")
	private final String brandName;

	@Schema(description = "아이템명")
	private final String name;

	@Schema(description = "대표 사진 URL")
	private final String coverImageUrl;
}
