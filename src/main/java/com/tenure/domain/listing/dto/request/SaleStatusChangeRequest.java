package com.tenure.domain.listing.dto.request;

import com.tenure.domain.item.enums.SaleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매 상태 변경. 메뉴 모달(2825:59974)이 주는 선택지는 '미판매로 변경하기' 와
 * '판매 완료로 변경하기' 둘뿐이라 ON_SALE 은 받지 않는다.
 *
 * 다시 팔려면 판매 전환(API-111)을 거치며 그때 새 판매 등록이 생긴다 (DEC-026).
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "판매 상태 변경 요청")
public class SaleStatusChangeRequest {

	@Schema(description = "바꿀 상태. NOT_FOR_SALE 또는 SOLD", example = "NOT_FOR_SALE")
	@NotNull
	private SaleStatus saleStatus;
}
