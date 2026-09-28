package com.tenure.domain.address.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 배송지 한 건. 목록은 기본 배송지를 맨 위에 두고 최근 등록 순으로 내려준다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "배송지")
public class ShippingAddressResponse {

	@Schema(description = "배송지 id")
	private final Long id;

	@Schema(description = "수령인", example = "홍길동")
	private final String recipientName;

	@Schema(description = "연락처", example = "010-1234-5678")
	private final String phone;

	@Schema(description = "우편번호", example = "07027")
	private final String zipcode;

	@Schema(description = "도로명주소", example = "서울특별시 동작구 상도로 369")
	private final String address;

	@Schema(description = "상세 주소", example = "104동 201호")
	private final String addressDetail;

	@Schema(description = "배송 요청사항")
	private final String deliveryRequest;

	@Schema(description = "기본 배송지면 true")
	private final Boolean isDefault;
}
