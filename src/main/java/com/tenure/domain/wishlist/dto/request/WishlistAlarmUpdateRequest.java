package com.tenure.domain.wishlist.dto.request;

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
@Schema(description = "위시 알림 on/off 요청")
public class WishlistAlarmUpdateRequest {

	@Schema(description = "알림 벨 on/off", example = "false")
	@NotNull
	private Boolean alarmEnabled;
}
