package com.tenure.domain.address.dto.request;

import com.tenure.domain.address.enums.DeliveryRequestType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배송지 추가 요청.
 *
 * 길이 제약은 컬럼 길이와 맞춰 둔다 — 넘기면 DB 에서 잘리거나 끊기는데 그건 400 으로 돌려줄 일이다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "배송지 추가 요청")
public class ShippingAddressCreateRequest {

	@Schema(description = "수령인", example = "홍길동")
	@NotBlank
	@Size(max = 50)
	private String recipientName;

	@Schema(description = "연락처", example = "010-1234-5678")
	@NotBlank
	@Size(max = 20)
	private String phone;

	@Schema(description = "우편번호. 주소 검색이 도로명주소와 함께 돌려준다", example = "07027")
	@NotBlank
	@Size(max = 10)
	private String zipcode;

	@Schema(description = "도로명주소", example = "서울특별시 동작구 상도로 369")
	@NotBlank
	@Size(max = 255)
	private String address;

	@Schema(description = "상세 주소", example = "104동 201호")
	@Size(max = 255)
	private String addressDetail;

	@Schema(description = "배송 요청사항 유형. 고르지 않으면 null")
	private DeliveryRequestType deliveryRequestType;

	@Schema(description = "직접 입력한 요청사항. deliveryRequestType 이 DIRECT 일 때만 보낸다",
			example = "배송 전에 미리 연락 바랍니다.")
	@Size(max = 50)
	private String deliveryRequest;

	/** 첫 배송지는 고를 여지가 없어 이 값과 무관하게 기본이 된다. */
	@Schema(description = "기본 배송지로 설정할지. 첫 배송지는 자동으로 기본이다")
	private Boolean isDefault;
}
