package com.tenure.domain.offer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 받은 제안 한 행의 제안자 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "제안자 정보")
public class OfferBuyerSummaryResponse {

	@Schema(description = "닉네임", example = "sujun_record")
	private final String nickname;

	@Schema(description = "프로필 사진", example = "https://cdn.tenure.app/profile/2026/06/cd34.jpg")
	private final String profileImageUrl;
}
