package com.tenure.domain.item.dto.response;

import com.tenure.domain.item.enums.SaleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 내 옷장 목록의 한 행.
 *
 * 태그 시트는 그리는 것이 달라 TaggableItemResponse 로 따로 둔다.
 *
 * saleStatus 는 item.sale_status 를 그대로 싣는다 (DEC-026). '미판매'가 판매 등록이 아예 없는
 * 경우와 판매를 내린 경우 둘 다인 것은 그대로지만, 아이템이 값을 들고 있어 합칠 일이 없다.
 *
 * listing 은 열린 판매 등록이 있을 때만 채운다 — 행에 가격을 그리는 데 쓴다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "아이템 요약")
public class ItemSummaryResponse {

	@Schema(description = "아이템 id", example = "1")
	private final Long id;

	@Schema(description = "브랜드명", example = "Levis")
	private final String brandName;

	@Schema(description = "아이템명", example = "LVC 1955 501")
	private final String name;

	@Schema(description = "대표 사진 URL")
	private final String coverImageUrl;

	@Schema(description = "판매 상태. 판매 등록이 없으면 NOT_FOR_SALE", example = "NOT_FOR_SALE")
	private final SaleStatus saleStatus;

	@Schema(description = "'최근 착용 N일 전' 계산 기준. 인증이 없으면 null", example = "2026-05-26")
	private final LocalDate lastWornDate;

	@Schema(description = "판매 조건. 판매 등록이 없으면 null")
	private final ItemListingSummaryResponse listing;
}
