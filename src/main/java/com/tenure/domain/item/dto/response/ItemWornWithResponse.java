package com.tenure.domain.item.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** '자주 같이 입은 옷' 칩. 같은 게시물에 함께 태그된 횟수 상위 아이템이다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "자주 같이 입은 옷")
public class ItemWornWithResponse {

	@Schema(description = "아이템 id", example = "2")
	private final Long itemId;

	@Schema(description = "브랜드명", example = "Our Legacy")
	private final String brandName;

	@Schema(description = "아이템명", example = "Mohair Knit")
	private final String name;
}
