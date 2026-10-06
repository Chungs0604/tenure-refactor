package com.tenure.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 채팅방 헤더의 상대 — 목록과 달리 닉네임만 내려줌 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "채팅방 헤더의 상대")
public class ChatHeaderCounterpartResponse {

	@Schema(description = "닉네임", example = "yujin_closet")
	private final String nickname;
}
