package com.tenure.domain.listing.dto.response;

import com.tenure.domain.item.enums.SaleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 판매 상태 변경 결과. 키가 아이템이다 — 상태는 item 이 갖는다 (DEC-026). */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "판매 상태")
public class SaleStatusResponse {

	@Schema(description = "아이템 id", example = "1")
	private final Long itemId;

	@Schema(description = "바뀐 판매 상태", example = "NOT_FOR_SALE")
	private final SaleStatus saleStatus;
}
