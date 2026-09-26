package com.tenure.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 회원가입 결과 (API-012). 가입 직후 자동 로그인된다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "회원가입 결과")
public class UserCreateResponse {

	@Schema(description = "생성된 사용자 id")
	private final Long id;

	@Schema(description = "액세스 토큰")
	private final String accessToken;

	@Schema(description = "리프레시 토큰")
	private final String refreshToken;
}
