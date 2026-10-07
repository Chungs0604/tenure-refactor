package com.tenure.domain.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "전체 읽음 처리 결과")
public class NotificationReadAllResponse {

	@Schema(description = "읽음 처리된 알림 수", example = "1")
	private final Integer updatedCount;
}
