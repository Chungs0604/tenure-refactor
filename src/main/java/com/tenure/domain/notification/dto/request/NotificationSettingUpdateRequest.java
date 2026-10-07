package com.tenure.domain.notification.dto.request;

import com.tenure.domain.notification.enums.NotificationSettingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 수신 설정 변경. 부분 갱신 — 보낸 유형만 바뀐다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "알림 수신 설정 변경 요청")
public class NotificationSettingUpdateRequest {

	@Schema(description = "바꿀 항목들")
	@NotEmpty
	@Valid
	private List<SettingItem> settings;

	@Getter
	@Builder
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	@Schema(description = "설정 항목 하나")
	public static class SettingItem {

		@Schema(description = "설정 유형. API-040 의 표 참고", example = "FOLLOW")
		@NotNull
		private NotificationSettingType settingType;

		@Schema(description = "수신 여부", example = "false")
		@NotNull
		private Boolean enabled;
	}
}
