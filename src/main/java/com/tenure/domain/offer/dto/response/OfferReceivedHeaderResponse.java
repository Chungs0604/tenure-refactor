package com.tenure.domain.offer.dto.response;

import com.tenure.domain.item.enums.SaleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 받은 제안·요청 목록 헤더. 판매 중이면 price 가 채워지고 미판매면 null */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "받은 제안 목록 헤더")
public class OfferReceivedHeaderResponse {

	@Schema(description = "브랜드명", example = "Levis")
	private final String brandName;

	@Schema(description = "아이템명", example = "LVC 1955 501")
	private final String name;

	@Schema(description = "대표 이미지", example = "https://cdn.tenure.app/post/2026/06/ab12.jpg")
	private final String coverImageUrl;

	@Schema(description = "판매 상태 — 판매 상품 / 미판매 아이템 표기를 가름", example = "ON_SALE")
	private final SaleStatus saleStatus;

	@Schema(description = "판매 중일 때만 — 판매가", example = "320000")
	private final String price;
}
