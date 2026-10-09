package com.tenure.domain.address.controller;

import com.tenure.domain.address.dto.request.ShippingAddressCreateRequest;
import com.tenure.domain.address.dto.request.ShippingAddressUpdateRequest;
import com.tenure.domain.address.dto.response.ShippingAddressIdResponse;
import com.tenure.domain.address.dto.response.ShippingAddressResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
			description = "가입 시 받는 첫 배송지만 isDefault 와 무관하게 기본이 된다(회원가입 API 가 처리). "
					+ "이 API 로 추가하는 배송지는 isDefault 를 그대로 따르며, 기본으로 추가하면 기존 기본 배송지가 내려간다.")
	@ApiResponse(responseCode = "200", description = "추가 성공")
	ResponseEntity<BaseResponse<ShippingAddressIdResponse>> create(
			@Parameter(hidden = true) AuthUser authUser,
			@Valid ShippingAddressCreateRequest request);

	@Operation(summary = "배송지 수정",
			description = "보낸 값으로 전체를 교체한다. 이력을 남기지 않으며 과거 거래에는 영향이 없다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "수정 성공"),
			@ApiResponse(responseCode = "403", description = "ADDRESS_403 — 본인의 배송지가 아님"),
			@ApiResponse(responseCode = "404", description = "ADDRESS_404 — 배송지를 찾을 수 없음")
	})
	ResponseEntity<BaseResponse<ShippingAddressIdResponse>> update(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "배송지 id", example = "1") Long addressId,
			@Valid ShippingAddressUpdateRequest request);

	@Operation(summary = "배송지 삭제",
			description = "하드 삭제다. 기본 배송지는 다른 배송지를 기본으로 지정한 뒤에 지울 수 있다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "삭제 성공"),
			@ApiResponse(responseCode = "403", description = "ADDRESS_403 — 본인의 배송지가 아님"),
			@ApiResponse(responseCode = "404", description = "ADDRESS_404 — 배송지를 찾을 수 없음"),
			@ApiResponse(responseCode = "409", description = "ADDRESS_409_LAST_DEFAULT — 기본 배송지")
	})
	ResponseEntity<BaseResponse<Void>> delete(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "배송지 id", example = "1") Long addressId);

	@Operation(summary = "기본 배송지 지정",
			description = "기존 기본 배송지를 내리고 지정한다. 이미 기본이면 아무것도 바뀌지 않는다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "지정 성공"),
			@ApiResponse(responseCode = "403", description = "ADDRESS_403 — 본인의 배송지가 아님"),
			@ApiResponse(responseCode = "404", description = "ADDRESS_404 — 배송지를 찾을 수 없음")
	})
	ResponseEntity<BaseResponse<Void>> changeDefault(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "배송지 id", example = "1") Long addressId);
}
