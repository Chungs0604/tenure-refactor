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

/** 비밀번호 재설정 요청 (API-006). 인증번호 확인으로 받은 토큰이 필요하다. */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "비밀번호 재설정 요청")
public class PasswordResetRequest {

	@Schema(description = "비밀번호를 바꿀 계정의 이메일", example = "gildong@naver.com")
	@NotBlank
	@Email
	private String email;

	@Schema(description = "인증번호 확인(API-005)으로 받은 1회용 토큰. purpose 가 PASSWORD_RESET 이어야 한다")
	@NotBlank
	private String verificationToken;

	@Schema(description = "새 비밀번호 (8자 이상)")
	@NotBlank
	@Size(min = 8)
	private String newPassword;
}
