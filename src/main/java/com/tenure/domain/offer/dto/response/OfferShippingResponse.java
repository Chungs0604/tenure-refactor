package com.tenure.domain.offer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 제안서 상세의 배송지. 제안 생성 시점 스냅샷이라 이후 주소록 변경과 무관함 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "제안서 배송지")
public class OfferShippingResponse {

	@Schema(description = "받는 분", example = "홍길동")
	private final String recipientName;

	@Schema(description = "연락처", example = "010-1234-5678")
	private final String phone;

	@Schema(description = "주소", example = "서울특별시 동작구 상도로 369")
	private final String address;

	@Schema(description = "상세주소", example = "104동 201호")
	private final String addressDetail;
}
