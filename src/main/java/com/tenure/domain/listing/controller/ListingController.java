package com.tenure.domain.listing.controller;

import com.tenure.domain.listing.dto.request.FeaturedPostsReplaceRequest;
import com.tenure.domain.listing.dto.request.ListingCreateRequest;
import com.tenure.domain.listing.dto.response.FeaturedPostResponse;
import com.tenure.domain.listing.dto.response.ListingIdResponse;
import com.tenure.domain.listing.service.ListingService;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class ListingController implements ListingApi {

	private final ListingService listingService;

	// 판매 정보 수정 — 화면이 전체 폼이라 PUT 이다
	@Override
	@PutMapping("/{listingId}")
	public ResponseEntity<BaseResponse<ListingIdResponse>> update(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long listingId,
			@RequestBody ListingCreateRequest request
	) {
		ListingIdResponse response = listingService.update(authUser.id(), listingId, request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 대표 OOTD 교체 — 상세 화면 상단 캐러셀만 따로 바꾸는 전용 엔드포인트
	@Override
	@PutMapping("/{listingId}/featured-posts")
	public ResponseEntity<BaseResponse<List<FeaturedPostResponse>>> replaceFeaturedPosts(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long listingId,
			@RequestBody FeaturedPostsReplaceRequest request
	) {
		List<FeaturedPostResponse> response = listingService.replaceFeaturedPosts(
				authUser.id(), listingId, request.getFeaturedPostIds());
		return ResponseEntity.ok(BaseResponse.success(response));
	}
}
