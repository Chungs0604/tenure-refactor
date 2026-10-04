package com.tenure.domain.listing.dto.request;

import com.tenure.domain.category.enums.MeasurementField;
import com.tenure.domain.item.enums.ConditionCode;
import com.tenure.domain.listing.enums.FeeBearer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매 전환·판매 정보 수정 요청. 두 화면이 같은 전체 폼이라 요청도 같다.
 *
 * 기본 사용자는 feeBearer·shippingFee 를 보낼 수 없다 — 화면에서 칩이 비활성이고 배송비 칸이
 * '0원' 으로 잠겨 있다(3342:33318). 보내면 403 이다.
 *
 * featuredPostIds 는 보낸 순서가 노출 순서가 된다 — 선택 화면(2825:52006)이 고른 순서를
 * 카드 우상단에 1·2·3·4 배지로 표시하고 상세의 가로 스크롤이 그 순서를 따른다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "판매 전환 요청")
public class ListingCreateRequest {

	@Schema(description = "아이템 정보. 폼이 다시 받는다")
	@NotNull
	@Valid
	private ListingItemRequest item;

	@Schema(description = "가격(원)", example = "320000")
	@NotNull
	@Min(1)
	@Max(100_000_000)
	private Integer price;

	@Schema(description = "배송비(원). 0 이면 판매자 부담. 기본 사용자는 보낼 수 없다", example = "5000")
	@Min(0)
	private Integer shippingFee;

	@Schema(description = "수수료 부담. 기본 사용자는 보낼 수 없다", example = "SELLER")
	private FeeBearer feeBearer;

	@Schema(description = "판매자 설명", example = "핏은 전체적으로 여유 있는 편이라 편하게 착용하기 좋습니다.")
	private String sellerNote;

	@Schema(description = "대표 OOTD 게시물 id. 보낸 순서가 노출 순서다", example = "[12, 34]")
	@NotEmpty
	@Size(min = 1, max = 5)
	private List<Long> featuredPostIds;

	/**
	 * 실측. 키는 API-090 의 measurementFields 로 받은 목록이며 그 목록과 정확히 일치해야 한다 —
	 * 세트에 없는 키가 오거나 세트의 키가 빠지면 400 이다.
	 *
	 * 이름 붙은 10개 필드 대신 맵으로 받는다. 대분류마다 쓰는 항목이 4~6개뿐이라
	 * 나머지가 null 로 끼는 것을 없앴다.
	 */
	@Schema(description = "실측. 키는 API-090 의 measurementFields 값이다. 실측을 받지 않는 대분류는 보내지 않는다",
			example = "{\"SHOULDER_WIDTH\": 45.0, \"CHEST_WIDTH\": 52.0, \"SLEEVE_LENGTH\": 61.0, \"TOTAL_LENGTH\": 70.0}")
	private Map<MeasurementField, @DecimalMin("0.0") @Digits(integer = 3, fraction = 1) BigDecimal> measurements;

	@Schema(description = "상태 이상 체크", example = "[\"STAIN\", \"PILLING_DISCOLOR\"]")
	private List<ConditionCode> conditions;
}
