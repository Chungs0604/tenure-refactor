package com.tenure.domain.address.controller;

import com.tenure.domain.address.dto.request.ShippingAddressCreateRequest;
import com.tenure.domain.address.dto.response.ShippingAddressIdResponse;
import com.tenure.domain.address.dto.response.ShippingAddressResponse;
import com.tenure.domain.address.service.AddressService;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipping-addresses")
@RequiredArgsConstructor
public class AddressController implements AddressApi {

	private final AddressService addressService;

	// 배송지 목록
	@Override
	@GetMapping
	public ResponseEntity<BaseResponse<List<ShippingAddressResponse>>> getAddresses(
			@AuthenticationPrincipal AuthUser authUser
	) {
		List<ShippingAddressResponse> response = addressService.getAddresses(authUser.id());
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 배송지 추가
	@Override
	@PostMapping
	public ResponseEntity<BaseResponse<ShippingAddressIdResponse>> create(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestBody ShippingAddressCreateRequest request
	) {
		ShippingAddressIdResponse response = addressService.create(authUser.id(), request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}
}
