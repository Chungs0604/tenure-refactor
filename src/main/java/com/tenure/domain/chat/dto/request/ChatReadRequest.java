package com.tenure.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "읽음 처리 요청")
public class ChatReadRequest {

	@Schema(description = "마지막으로 읽은 메시지 id", example = "1")
	@NotNull
	private Long lastReadMessageId;
}
