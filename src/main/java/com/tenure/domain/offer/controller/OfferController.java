package com.tenure.domain.offer.controller;

import com.tenure.domain.offer.dto.response.OfferAcceptResponse;
import com.tenure.domain.offer.dto.response.OfferDetailResponse;
import com.tenure.domain.offer.service.OfferService;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/offers")
@RequiredArgsConstructor
public class OfferController implements OfferApi {

	private final OfferService offerService;

	// 제안서 상세
	@Override
	@GetMapping("/{offerId}")
	public ResponseEntity<BaseResponse<OfferDetailResponse>> getDetail(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long offerId
	) {
		OfferDetailResponse response = offerService.getDetail(authUser.id(), offerId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 제안 취소
	@Override
	@DeleteMapping("/{offerId}")
	public ResponseEntity<BaseResponse<Void>> cancel(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long offerId
	) {
		offerService.cancel(authUser.id(), offerId);
		return ResponseEntity.ok(BaseResponse.success());
	}

	// 제안 수락 → 거래 생성
	@Override
	@PostMapping("/{offerId}/accept")
	public ResponseEntity<BaseResponse<OfferAcceptResponse>> accept(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long offerId
	) {
		OfferAcceptResponse response = offerService.accept(authUser.id(), offerId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 제안 거절
	@Override
	@PostMapping("/{offerId}/reject")
	public ResponseEntity<BaseResponse<Void>> reject(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long offerId
	) {
		offerService.reject(authUser.id(), offerId);
		return ResponseEntity.ok(BaseResponse.success());
	}
}
