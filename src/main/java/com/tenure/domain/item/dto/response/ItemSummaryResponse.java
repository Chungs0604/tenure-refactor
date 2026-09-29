package com.tenure.domain.item.dto.response;

import com.tenure.domain.listing.enums.SaleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 내 옷장 목록의 한 행.
 *
 * saleStatus 는 아이템 레벨로 평탄화한 값이다 — '미판매'가 판매 등록이 아예 없는 경우와
 * 판매를 내린 경우 둘 다라서, 화면이 두 갈래를 구분할 필요가 없도록 서버가 합쳐 내려준다.
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

	@Schema(description = "관심 N명", example = "5")
	private final Integer wishCount;

	@Schema(description = "구매 제안 허용", example = "false")
	private final Boolean offerAllowed;

	@Schema(description = "'최근 착용 N일 전' 계산 기준. 인증이 없으면 null", example = "2026-05-26")
	private final LocalDate lastWornDate;

	@Schema(description = "판매 조건. 판매 등록이 없으면 null")
	private final ItemListingSummaryResponse listing;
}
