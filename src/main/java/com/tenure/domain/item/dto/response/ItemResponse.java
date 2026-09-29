package com.tenure.domain.item.dto.response;

import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.listing.enums.SaleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 아이템 상세 (소유자 시점).
 *
 * 하단 CTA 는 saleStatus 로 갈린다 — 판매 중이면 '판매 페이지로', 그 외에는 '공개 페이지로' 다.
 * 판매 전환 진입은 이 화면이 아니라 목록의 행 버튼이다.
 *
 * 실측·상태 이상은 이 화면에 그려지지 않지만 판매 전환 폼이 이 값으로 빈칸을 채운다.
 * 그 둘은 listing 도메인을 구현할 때 채운다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "아이템 상세")
public class ItemResponse {

	@Schema(description = "아이템 id", example = "1")
	private final Long id;

	@Schema(description = "아이템명", example = "LVC 1955 501")
	private final String name;

	@Schema(description = "브랜드명", example = "Levis")
	private final String brandName;

	@Schema(description = "대분류 이름. '상의 · 긴소매 티셔츠' 의 앞쪽", example = "상의")
	private final String rootCategoryName;

	@Schema(description = "세부 카테고리 id. 수정 화면의 초기값", example = "116")
	private final Long categoryId;

	@Schema(description = "세부 카테고리 이름", example = "긴소매 티셔츠")
	private final String categoryName;

	@Schema(description = "대표 사진 URL")
	private final String coverImageUrl;

	@Schema(description = "사이즈. 고르지 않았으면 null", example = "L")
	private final String sizeLabel;

	@Schema(description = "착용 대상", example = "MALE")
	private final WearTarget wearTarget;

	@Schema(description = "제품 설명")
	private final String description;

	@Schema(description = "최초 보유 날짜. 입력하지 않았으면 null", example = "2026-05-26")
	private final LocalDate firstOwnedDate;

	@Schema(description = "판매 상태. 판매 등록이 없으면 NOT_FOR_SALE", example = "NOT_FOR_SALE")
	private final SaleStatus saleStatus;

	@Schema(description = "관심 N명", example = "14")
	private final Integer wishCount;

	@Schema(description = "구매 제안 허용", example = "false")
	private final Boolean offerAllowed;

	@Schema(description = "소유 구간 카드. 최신 구간이 먼저 온다")
	private final List<ItemOwnershipResponse> ownerships;

	@Schema(description = "'자주 같이 입은 옷' 칩. 최대 3건")
	private final List<ItemWornWithResponse> frequentlyWornWith;

	@Schema(description = "최근 착용일. 인증이 없으면 null", example = "2026-05-26")
	private final LocalDate recentWornDate;

	@Schema(description = "전체 인증 횟수", example = "20")
	private final Integer totalWearCount;

	@Schema(description = "판매 조건. 판매 등록이 없으면 null")
	private final ItemListingSummaryResponse listing;
}
