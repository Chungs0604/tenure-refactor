package com.tenure.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 인증번호 발송 결과 (API-004). */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "인증번호 발송 결과")
public class EmailVerificationSendResponse {

	@Schema(description = "인증번호 만료 시각 (발급 +3분)")
	private final LocalDateTime expiresAt;
}
