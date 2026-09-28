package com.tenure.domain.address.controller;

import com.tenure.domain.address.dto.request.ShippingAddressCreateRequest;
import com.tenure.domain.address.dto.response.ShippingAddressIdResponse;
import com.tenure.domain.address.dto.response.ShippingAddressResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/**
 * 파라미터 제약을 이 인터페이스에만 선언한다 — 근거는 {@code UserApi} 의 주석에 적어 두었다.
 */
@Validated
@Tag(name = "배송지", description = "배송지 주소록 API")
public interface AddressApi {

	@Operation(summary = "배송지 목록",
			description = "기본 배송지가 맨 위, 그다음 최근 등록 순이다. 개수가 적어 페이징하지 않는다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	ResponseEntity<BaseResponse<List<ShippingAddressResponse>>> getAddresses(
			@Parameter(hidden = true) AuthUser authUser);

	@Operation(summary = "배송지 추가",
			description = "첫 배송지는 isDefault 와 무관하게 기본이 된다. "
					+ "기본으로 추가하면 기존 기본 배송지가 내려간다.")
	@ApiResponse(responseCode = "200", description = "추가 성공")
	ResponseEntity<BaseResponse<ShippingAddressIdResponse>> create(
			@Parameter(hidden = true) AuthUser authUser,
			@Valid ShippingAddressCreateRequest request);
}
