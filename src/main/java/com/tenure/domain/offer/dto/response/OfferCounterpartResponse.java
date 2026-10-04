package com.tenure.domain.offer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 제안서 상세의 상대방 — 구매자 시점이면 판매자, 판매자 시점이면 구매자 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "제안 상대방")
public class OfferCounterpartResponse {

	@Schema(description = "닉네임", example = "yujin_closet")
	private final String nickname;
}
