package com.tenure.domain.item.dto.request;

import com.tenure.domain.item.enums.WearTarget;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아이템 상세 수정 요청. 보낸 항목만 바뀐다 — 비운 항목은 유지한다.
 *
 * 최초 보유 날짜는 받지 않는다. 등록 시점에 정해지고 그 뒤로는 아무도 바꿀 수 없다 (DEC-007) —
 * 그 값이 최초 소유 구간의 시작일이고, 구매자가 보고 산 'Tenure 기록 기준' 의 첫 줄이다.
 *
 * 실측·상태 이상도 여기서 받지 않는다. 상세 수정 바텀시트에 입력란이 없고 판매 전환이 담당한다.
 *
 * offerAllowed 가 여기 있는 이유는 미판매 아이템에 판매 등록 행이 없어서다 — 판매 상태 변경
 * API 로는 켤 수 없다 (DEC-024).
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "아이템 수정 요청")
public class ItemUpdateRequest {

	@Schema(description = "아이템명", example = "LVC 1955 501")
	@Size(max = 100)
	private String name;

	@Schema(description = "브랜드명", example = "Levis")
	@Size(max = 100)
	private String brandName;

	@Schema(description = "세부 카테고리 id (depth=2)", example = "116")
	private Long categoryId;

	@Schema(description = "사이즈. 카테고리 트리가 준 label 을 그대로 보낸다", example = "L")
	@Size(max = 20)
	private String sizeLabel;

	@Schema(description = "착용 대상", example = "MALE")
	private WearTarget wearTarget;

	@Schema(description = "대표 사진 교체", example = "item/2026/09/ab12.jpg")
	@Size(max = 500)
	private String coverImageKey;

	@Schema(description = "제품 설명")
	private String description;

	@Schema(description = "구매 제안 허용 토글", example = "true")
	private Boolean offerAllowed;
}
