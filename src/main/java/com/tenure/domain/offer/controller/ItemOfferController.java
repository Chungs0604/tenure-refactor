package com.tenure.domain.offer.controller;

import com.tenure.domain.offer.dto.request.OfferCreateRequest;
import com.tenure.domain.offer.dto.response.OfferCreateResponse;
import com.tenure.domain.offer.dto.response.OfferPreviewResponse;
import com.tenure.domain.offer.dto.response.OfferReceivedListResponse;
import com.tenure.domain.offer.service.OfferService;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemOfferController implements ItemOfferApi {

	private final OfferService offerService;

	// 결제 금액 미리보기
	@Override
	@GetMapping("/{itemId}/offer-preview")
	public ResponseEntity<BaseResponse<OfferPreviewResponse>> preview(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long itemId,
			@RequestParam(required = false) Integer offerPrice
	) {
		OfferPreviewResponse response = offerService.preview(itemId, offerPrice);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 구매 제안 / 거래 의사 전송
	@Override
	@PostMapping("/{itemId}/offers")
	public ResponseEntity<BaseResponse<OfferCreateResponse>> create(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long itemId,
			@RequestBody OfferCreateRequest request
	) {
		OfferCreateResponse response = offerService.create(authUser.id(), itemId, request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 아이템별 받은 제안·요청 목록
	@Override
	@GetMapping("/{itemId}/offers")
	public ResponseEntity<BaseResponse<OfferReceivedListResponse>> getReceivedList(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long itemId
	) {
		OfferReceivedListResponse response = offerService.getReceivedList(authUser.id(), itemId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}
}
