package com.tenure.domain.wishlist.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "관심 등록·해제 후 갱신된 관심 수")
public class WishCountResponse {

	@Schema(description = "갱신된 값", example = "1")
	private final Integer wishCount;
}
