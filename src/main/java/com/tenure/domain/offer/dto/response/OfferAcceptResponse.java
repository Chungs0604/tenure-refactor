package com.tenure.domain.offer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 제안 수락 결과 — 새로 생긴 거래와 함께 취소된 다른 제안 수 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "제안 수락 결과")
public class OfferAcceptResponse {

	@Schema(description = "생성된 거래 id", example = "1")
	private final Long orderId;

	@Schema(description = "주문 번호", example = "2026042919502395")
	private final String orderNo;

	@Schema(description = "함께 자동 취소된 다른 제안 수", example = "1")
	private final Integer autoCanceledCount;
}
