package com.tenure.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "채팅방 개설 결과")
public class ChatRoomOpenResponse {

	@Schema(description = "채팅방 id. 이미 있던 방이면 그 id", example = "1")
	private final Long roomId;
}
