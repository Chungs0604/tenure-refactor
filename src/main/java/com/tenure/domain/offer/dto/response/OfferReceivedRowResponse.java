package com.tenure.domain.offer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 받은 제안 한 행. 탭하면 제안서 상세(id)로 이동함 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "받은 제안 한 행")
public class OfferReceivedRowResponse {

	@Schema(description = "제안 id", example = "1")
	private final Long offerId;

	@Schema(description = "제안자")
	private final OfferBuyerSummaryResponse buyer;

	@Schema(description = "제안 금액. 거래 의사면 판매가와 같다", example = "360000")
	private final String offerPrice;

	@Schema(description = "제안 만료 시각. 남은 시간 계산 기준", example = "2026-06-21T18:30:00")
	private final LocalDateTime expiresAt;
}
