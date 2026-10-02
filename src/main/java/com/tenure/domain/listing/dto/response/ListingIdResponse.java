package com.tenure.domain.listing.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 판매 전환·수정이 돌려주는 판매 등록 id. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "판매 등록 id")
public class ListingIdResponse {

	@Schema(description = "판매 등록 id", example = "1")
	private final Long id;
}
