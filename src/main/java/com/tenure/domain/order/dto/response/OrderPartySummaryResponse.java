package com.tenure.domain.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 구매자/판매자 정보. 둘 다 뷰어 역할과 무관하게 항상 내려간다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "거래 상대/당사자 정보")
public class OrderPartySummaryResponse {

	@Schema(description = "닉네임", example = "seoyeon")
	private final String nickname;

	@Schema(description = "프로필 사진 URL")
	private final String profileImageUrl;
}
