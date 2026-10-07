package com.tenure.domain.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "안읽은 알림 수")
public class UnreadCountResponse {

	@Schema(description = "read_at 이 null 인 알림 수", example = "3")
	private final Integer unreadCount;
}
