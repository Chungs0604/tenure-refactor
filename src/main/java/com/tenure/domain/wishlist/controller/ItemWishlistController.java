package com.tenure.domain.wishlist.controller;

import com.tenure.domain.wishlist.dto.response.WishCountResponse;
import com.tenure.domain.wishlist.service.WishlistService;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemWishlistController implements ItemWishlistApi {

	private final WishlistService wishlistService;

	// 관심 등록
	@Override
	@PostMapping("/{itemId}/wishlist")
	public ResponseEntity<BaseResponse<WishCountResponse>> addToWishlist(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long itemId
	) {
		WishCountResponse response = wishlistService.addToWishlist(authUser.id(), itemId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 관심 해제
	@Override
	@DeleteMapping("/{itemId}/wishlist")
	public ResponseEntity<BaseResponse<WishCountResponse>> removeFromWishlist(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long itemId
	) {
		WishCountResponse response = wishlistService.removeFromWishlist(authUser.id(), itemId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}
}
