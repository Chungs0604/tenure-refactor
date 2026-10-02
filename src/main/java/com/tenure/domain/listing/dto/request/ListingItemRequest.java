package com.tenure.domain.listing.dto.request;

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
 * 판매 전환 폼이 다시 받는 아이템 정보.
 *
 * 판매 전환 화면(3342:32943)에 대표 사진·브랜드명·아이템명·카테고리·세부 카테고리·사이즈·
 * 착용 대상이 전부 있다. 한 폼·한 CTA 이므로 판매 등록과 같은 트랜잭션에서 아이템도 갱신한다.
 *
 * 사이즈는 이 화면에서만 필수 표시(*)가 붙는다 — item.size_label 은 nullable 이라
 * 그 필수를 여기서 잡는다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "판매 전환이 함께 갱신하는 아이템 정보")
public class ListingItemRequest {

	@Schema(description = "브랜드명", example = "Levis")
	@NotBlank
	@Size(max = 100)
	private String brandName;

	@Schema(description = "아이템명", example = "LVC 1955 501")
	@NotBlank
	@Size(max = 100)
	private String name;

	@Schema(description = "세부 카테고리 id (depth=2)", example = "116")
	@NotNull
	private Long categoryId;

	@Schema(description = "사이즈. 판매 전환에서는 필수다", example = "L")
	@NotBlank
	@Size(max = 20)
	private String sizeLabel;

	@Schema(description = "착용 대상", example = "MALE")
	@NotNull
	private WearTarget wearTarget;

	@Schema(description = "대표 사진. API-060 이 준 저장소 키", example = "item/2026/09/ab12.jpg")
	@NotBlank
	@Size(max = 500)
	private String coverImageKey;
}
