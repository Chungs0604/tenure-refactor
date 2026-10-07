package com.tenure.domain.notification.dto.response;

import com.tenure.domain.notification.enums.NotificationSettingType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "알림 설정 토글 한 줄")
public class NotificationSettingResponse {

	@Schema(description = "설정 유형", example = "TRADE_REQUEST")
	private final NotificationSettingType settingType;

	@Schema(description = "그룹 헤더", example = "필수")
	private final String group;

	@Schema(description = "토글 제목", example = "거래 요청")
	private final String title;

	@Schema(description = "토글 설명", example = "새로운 거래 요청이 오면 알려드려요.")
	private final String description;

	@Schema(description = "수신 여부", example = "true")
	private final Boolean enabled;
}
