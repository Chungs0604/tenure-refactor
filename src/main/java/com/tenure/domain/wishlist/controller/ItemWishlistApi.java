package com.tenure.domain.wishlist.controller;

import com.tenure.domain.wishlist.dto.response.WishCountResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/** 아이템 경로를 쓰는 관심 등록·해제 API(ItemOfferApi 와 같은 분리 기준). */
@Validated
@Tag(name = "위시리스트", description = "관심 등록·해제 API")
public interface ItemWishlistApi {

	@Operation(summary = "관심 등록",
			description = "같은 트랜잭션에서 item.wish_count 를 +1 한다. 판매 등록 여부와 무관하게 동작한다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "등록 성공"),
			@ApiResponse(responseCode = "404", description = "ITEM_404"),
			@ApiResponse(responseCode = "409", description = "WISHLIST_409 — 이미 관심 등록한 아이템")
	})
	ResponseEntity<BaseResponse<WishCountResponse>> addToWishlist(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "아이템 id", example = "1") Long itemId);

	@Operation(summary = "관심 해제", description = "같은 트랜잭션에서 item.wish_count 를 -1 한다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "해제 성공"),
			@ApiResponse(responseCode = "404", description = "WISHLIST_404")
	})
	ResponseEntity<BaseResponse<WishCountResponse>> removeFromWishlist(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "아이템 id", example = "1") Long itemId);
}
