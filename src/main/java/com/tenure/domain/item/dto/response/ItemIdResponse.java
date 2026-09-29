package com.tenure.domain.item.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 등록·수정이 돌려주는 아이템 id. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "아이템 id")
public class ItemIdResponse {

	@Schema(description = "아이템 id", example = "1")
	private final Long id;
}
