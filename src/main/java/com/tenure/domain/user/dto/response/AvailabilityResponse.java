package com.tenure.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 중복 확인 결과 (API-010·API-011). 중복이어도 예외를 던지지 않는다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "사용 가능 여부")
public class AvailabilityResponse {

	@Schema(description = "사용 가능하면 true", example = "true")
	private final boolean available;
}
