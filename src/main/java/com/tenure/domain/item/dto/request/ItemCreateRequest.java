package com.tenure.domain.item.dto.request;

import com.tenure.domain.item.enums.WearTarget;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아이템 등록 요청.
 *
 * 대표 사진은 키로 받는다 — 파일은 업로드 API 로 먼저 올린다.
 *
 * 실측·상태 이상은 받지 않는다. 등록 폼과 상세 수정 바텀시트 어디에도 입력란이 없고
 * 두 항목은 판매 전환 화면에만 있다.
 *
 * 사이즈는 카테고리마다 고를 수 있는 값이 달라 Service 가 판정한다 — 화면이 구분해야 하는
 * 실패라 ITEM_400_SIZE 로 돌려줘야 하고, Bean Validation 은 COMMON_400 이 된다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "아이템 등록 요청")
public class ItemCreateRequest {

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

	@Schema(description = "사이즈. 카테고리 트리가 준 label 을 그대로 보낸다. 비워도 된다", example = "L")
	@Size(max = 20)
	private String sizeLabel;

	@Schema(description = "착용 대상", example = "MALE")
	@NotNull
	private WearTarget wearTarget;

	@Schema(description = "업로드 API 가 돌려준 대표 사진 키",
			example = "item/2026/09/0f9b1c2d3e4f4a5b8c7d6e5f4a3b2c1d.jpg")
	@NotBlank
	@Size(max = 500)
	private String coverImageKey;

	@Schema(description = "최초 보유 날짜. 비우면 등록일부터 보유한 것으로 본다", example = "2026-05-26")
	@PastOrPresent
	private LocalDate firstOwnedDate;
}
