package com.tenure.domain.item.dto.request;

import com.tenure.domain.item.enums.WearTarget;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아이템 상세 수정 요청. 폼 전체를 다시 받는다 — 보낸 것만 바꾸는 부분 수정이 아니다.
 *
 * 상세 수정 바텀시트(2825:49332 외 9종)는 현재 값이 전부 채워진 채로 열리고 CTA 가
 * '초기화' / '적용하기' 둘뿐이라, 클라이언트는 언제나 이 필드들을 전부 보낸다.
 * 빠뜨린 값을 조용히 유지하는 대신 400 으로 드러나게 한다.
 *
 * 최초 보유 날짜는 받지 않는다. 등록 시점에 정해지고 그 뒤로는 아무도 바꿀 수 없다 (DEC-007) —
 * 그 값이 최초 소유 구간의 시작일이고, 구매자가 보고 산 'Tenure 기록 기준' 의 첫 줄이다.
 *
 * 실측·상태 이상도 여기서 받지 않는다. 시트에 입력란이 없고 판매 전환이 담당한다.
 *
 * 대표 사진과 구매 제안 허용도 이 시트에 없다 — 사진은 판매 전환(API-111·API-112)이,
 * 토글은 선택 모달의 API-116 이 맡는다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "아이템 수정 요청")
public class ItemUpdateRequest {

	@Schema(description = "아이템명", example = "LVC 1955 501")
	@NotBlank
	@Size(max = 100)
	private String name;

	@Schema(description = "브랜드명", example = "Levis")
	@NotBlank
	@Size(max = 100)
	private String brandName;

	@Schema(description = "세부 카테고리 id (depth=2)", example = "116")
	@NotNull
	private Long categoryId;

	/** 칩을 고르지 않은 아이템이 있어 비워 보낼 수 있다. */
	@Schema(description = "사이즈. 카테고리 트리가 준 label 을 그대로 보낸다", example = "L")
	@Size(max = 20)
	private String sizeLabel;

	@Schema(description = "착용 대상", example = "MALE")
	@NotNull
	private WearTarget wearTarget;
}
