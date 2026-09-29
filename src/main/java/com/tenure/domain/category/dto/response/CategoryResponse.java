package com.tenure.domain.category.dto.response;

import com.tenure.domain.category.enums.SizeScheme;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 카테고리 트리 한 노드.
 *
 * sizeScheme 과 sizeOptions 는 대분류에만 채운다 — 세부 카테고리는 부모의 칩을 그대로 쓴다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "카테고리")
public class CategoryResponse {

	@Schema(description = "카테고리 id. 아이템 등록에는 depth=2 의 id 를 보낸다", example = "116")
	private final Long id;

	@Schema(description = "이름", example = "긴소매 티셔츠")
	private final String name;

	@Schema(description = "1=카테고리, 2=세부 카테고리", example = "2")
	private final Integer depth;

	@Schema(description = "사이즈 칩 세트. 세부 카테고리는 null", example = "TOP")
	private final SizeScheme sizeScheme;

	@Schema(description = "이 대분류에서 고를 수 있는 사이즈 칩. 세부 카테고리는 빈 배열")
	private final List<SizeOptionResponse> sizeOptions;

	@Schema(description = "세부 카테고리")
	private final List<CategoryResponse> children;
}
