package com.tenure.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 액세스 토큰 재발급 요청 (API-003). */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "액세스 토큰 재발급 요청")
public class TokenReissueRequest {

	@Schema(description = "로그인 때 받은 리프레시 토큰")
	@NotBlank
	private String refreshToken;
}
