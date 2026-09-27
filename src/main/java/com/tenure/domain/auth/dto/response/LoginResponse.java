package com.tenure.domain.auth.dto.response;

import com.tenure.domain.user.enums.UserGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 로그인 결과 (API-001). */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "로그인 결과")
public class LoginResponse {

	@Schema(description = "액세스 토큰")
	private final String accessToken;

	@Schema(description = "리프레시 토큰 (Redis 보관)")
	private final String refreshToken;

	@Schema(description = "로그인한 사용자")
	private final UserSummary user;

	/** 로그인 직후 화면(홈·마이페이지 헤더)이 바로 쓰는 최소 정보. */
	@Getter
	@Builder
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	@Schema(description = "로그인한 사용자")
	public static class UserSummary {

		@Schema(description = "사용자 id", example = "1")
		private final Long id;

		@Schema(description = "닉네임", example = "gildong")
		private final String nickname;

		/** 저장소 키가 아니라 URL 을 내려준다 (DEC-006). 등록하지 않았으면 null 이고 화면이 기본 이미지를 쓴다. */
		@Schema(description = "프로필 이미지 URL. 미등록이면 null",
				example = "https://cdn.tenure.app/user/2026/06/ab12.jpg")
		private final String profileImageUrl;

		@Schema(description = "등급", example = "BASIC")
		private final UserGrade grade;
	}
}
