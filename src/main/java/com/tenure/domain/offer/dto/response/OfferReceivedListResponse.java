package com.tenure.domain.offer.dto.response;

import com.tenure.domain.offer.enums.OfferType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 아이템별 받은 제안·요청 목록. 만료된 제안은 빠짐 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "받은 제안·요청 목록")
public class OfferReceivedListResponse {

	@Schema(description = "헤더")
	private final OfferReceivedHeaderResponse item;

	@Schema(description = "구매 제안(미판매) / 거래 의사(판매 중) — 헤더 문구를 가른다", example = "PURCHASE_OFFER")
	private final OfferType offerType;

	@Schema(description = "총 건수", example = "3")
	private final Integer totalCount;

	@Schema(description = "제안 목록")
	private final List<OfferReceivedRowResponse> offers;
}
