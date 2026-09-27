package com.tenure.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 액세스 토큰 재발급 결과 (API-003). 리프레시 토큰은 그대로 쓴다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "액세스 토큰 재발급 결과")
public class TokenReissueResponse {

	@Schema(description = "새 액세스 토큰")
	private final String accessToken;
}
