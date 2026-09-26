package com.tenure.domain.auth.dto.request;

import com.tenure.domain.auth.enums.VerificationPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인증번호 발송 요청 (API-004).
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "인증번호 발송 요청")
public class EmailVerificationSendRequest {

	@Schema(description = "인증받을 이메일", example = "gildong@naver.com")
	@NotBlank
	@Email
	private String email;

	@Schema(description = "용도", example = "SIGN_UP")
	@NotNull
	private VerificationPurpose purpose;
}
