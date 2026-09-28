package com.tenure.domain.address.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배송지 수정 요청. 보낸 값으로 전체를 교체한다.
 *
 * 기본 배송지 여부는 여기 없다 — 목록에서 항목을 눌러 바꾸는 별도 동작이라 화면이 분리돼 있다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "배송지 수정 요청")
public class ShippingAddressUpdateRequest {

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

	@Schema(description = "배송 요청사항")
	@Size(max = 255)
	private String deliveryRequest;
}
