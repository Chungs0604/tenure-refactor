package com.tenure.domain.listing.controller;

import com.tenure.domain.listing.dto.request.ListingCreateRequest;
import com.tenure.domain.listing.dto.request.SaleStatusChangeRequest;
import com.tenure.domain.listing.dto.response.FeaturedPostResponse;
import com.tenure.domain.listing.dto.response.ItemDetailResponse;
import com.tenure.domain.listing.dto.response.ListingIdResponse;
import com.tenure.domain.listing.dto.response.SaleStatusResponse;
import com.tenure.domain.listing.service.ListingQueryService;
import com.tenure.domain.listing.service.ListingService;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemListingController implements ItemListingApi {

	private final ListingService listingService;
	private final ListingQueryService listingQueryService;

	// 아이템 상세 (공개 페이지)
	@Override
	@GetMapping("/{itemId}/detail")
	public ResponseEntity<BaseResponse<ItemDetailResponse>> getItemDetail(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long itemId
	) {
		ItemDetailResponse response = listingQueryService.getItemDetail(authUser.id(), itemId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 판매 전환
	@Override
	@PostMapping("/{itemId}/listings")
	public ResponseEntity<BaseResponse<ListingIdResponse>> create(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long itemId,
			@RequestBody ListingCreateRequest request
	) {
		ListingIdResponse response = listingService.create(authUser.id(), itemId, request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 판매 상태 변경 — 상태는 아이템이 갖는다
	@Override
	@PatchMapping("/{itemId}/sale-status")
	public ResponseEntity<BaseResponse<SaleStatusResponse>> changeSaleStatus(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long itemId,
			@RequestBody SaleStatusChangeRequest request
	) {
		SaleStatusResponse response =
				listingService.changeSaleStatus(authUser.id(), itemId, request.getSaleStatus());
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 대표 OOTD 전체 보기
	@Override
	@GetMapping("/{itemId}/featured-posts")
	public ResponseEntity<BaseResponse<List<FeaturedPostResponse>>> getFeaturedPosts(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long itemId
	) {
		List<FeaturedPostResponse> response = listingQueryService.getFeaturedPosts(itemId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}
}
