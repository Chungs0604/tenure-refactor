package com.tenure.domain.wishlist.controller;

import com.tenure.domain.wishlist.dto.request.WishlistAlarmUpdateRequest;
import com.tenure.domain.wishlist.dto.response.WishlistItemResponse;
import com.tenure.domain.wishlist.enums.WishlistTab;
import com.tenure.global.pagination.CursorResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/**
 * 위시리스트 경로(`/api/v1/wishlists`)를 쓰는 API. 관심 등록·해제는 아이템 경로를 써서
 * ItemWishlistApi 로 분리함(ItemOfferApi·ItemListingApi 와 같은 분리 기준).
 */
@Validated
@Tag(name = "위시리스트", description = "위시리스트 목록, 위시 알림 on/off API")
public interface WishlistApi {

	@Operation(summary = "위시리스트 목록",
			description = "탭(전체/판매중/거래중). 최신 관심 등록 순. 열린 판매 등록이 없으면 "
					+ "price 가 null 이고 화면은 그 자리에 '구매 제안' 링크를 그린다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	ResponseEntity<BaseResponse<CursorResponse<WishlistItemResponse>>> getWishlists(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "탭. 미지정 시 전체") WishlistTab tab,
			@Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 비운다") String cursor,
			@Parameter(description = "페이지 크기") @Min(1) @Max(100) int size);

	@Operation(summary = "위시 알림 on/off", description = "본인 항목만 바꿀 수 있다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "처리 성공"),
			@ApiResponse(responseCode = "403", description = "WISHLIST_403 — 본인의 위시리스트 항목이 아님"),
			@ApiResponse(responseCode = "404", description = "WISHLIST_404")
	})
	ResponseEntity<BaseResponse<Void>> updateAlarm(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "위시리스트 id", example = "1") Long wishlistId,
			@Valid WishlistAlarmUpdateRequest request);
}
