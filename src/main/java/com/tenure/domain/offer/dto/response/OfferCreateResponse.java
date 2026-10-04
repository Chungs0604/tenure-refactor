package com.tenure.domain.offer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 제안 전송 결과 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "제안 전송 결과")
public class OfferCreateResponse {

	@Schema(description = "생성된 제안 id", example = "1")
	private final Long offerId;

	@Schema(description = "제안 만료 시각. 생성 +24시간", example = "2026-06-21T18:30:00")
	private final LocalDateTime expiresAt;

	@Schema(description = "총 결제 금액", example = "386600")
	private final String totalPaymentAmount;
}
