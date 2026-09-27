package com.tenure.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 로그인 요청 (API-001). */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "로그인 요청")
public class LoginRequest {

	@Schema(description = "로그인 이메일", example = "gildong@naver.com")
	@NotBlank
	@Email
	private String email;

	@Schema(description = "비밀번호 (8자 이상)")
	@NotBlank
	@Size(min = 8)
	private String password;

	/** 화면의 `로그인 상태 유지`. refresh token 의 Redis TTL 이 길어진다. **/
	@Schema(description = "로그인 상태 유지. 기본 false", example = "false")
	private Boolean keepLoggedIn;
}
