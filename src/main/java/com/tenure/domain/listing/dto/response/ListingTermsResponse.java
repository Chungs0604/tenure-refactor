package com.tenure.domain.listing.dto.response;

import com.tenure.domain.category.enums.MeasurementField;
import com.tenure.domain.category.enums.MeasurementScheme;
import com.tenure.domain.item.enums.ConditionCode;
import com.tenure.domain.listing.enums.FeeBearer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 열린 판매 등록의 조건.
 *
 * 판매를 내리면 닫히므로 미판매·판매완료 상태에서는 이 블록 자체가 null 이고,
 * 화면은 가격·배송비·수수료 부담·상품 설명·실측·상태 이상 섹션을 통째로 접는다(2825:60351).
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "판매 조건")
public class ListingTermsResponse {

	@Schema(description = "판매 등록 id. 판매 정보 수정의 입력 키", example = "1")
	private final Long id;

	@Schema(description = "가격", example = "128000")
	private final String price;

	@Schema(description = "배송비. 0 이면 판매자 부담", example = "5000")
	private final String shippingFee;

	@Schema(description = "수수료 부담", example = "SELLER")
	private final FeeBearer feeBearer;

	@Schema(description = "화면 라벨은 '상품 설명'", example = "핏은 전체적으로 여유 있는 편입니다.")
	private final String sellerNote;

	/**
	 * 실측 세트. 화면이 '실측 입력 · 바지' 처럼 세트 이름을 헤더에 쓴다.
	 *
	 * categoryName 은 세부 카테고리('긴소매 티셔츠')라 이 헤더를 그릴 수 없어 따로 싣는다.
	 */
	@Schema(description = "실측 항목 세트. NONE 이면 실측 섹션 자체가 없다", example = "BOTTOM")
	private final MeasurementScheme measurementScheme;

	/** 채워진 항목만 담긴다 — 비어 있는 칸은 키가 없어 화면이 그릴 것만 받는다. */
	@Schema(description = "실측. 키는 API-090 의 measurementFields 값이다",
			example = "{\"SHOULDER_WIDTH\": 45.0, \"CHEST_WIDTH\": 52.0}")
	private final Map<MeasurementField, BigDecimal> measurements;

	@Schema(description = "상태 이상 체크")
	private final List<ConditionCode> conditions;
}
