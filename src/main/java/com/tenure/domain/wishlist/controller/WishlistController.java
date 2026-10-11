package com.tenure.domain.wishlist.controller;

import com.tenure.domain.wishlist.dto.request.WishlistAlarmUpdateRequest;
import com.tenure.domain.wishlist.dto.response.WishlistItemResponse;
import com.tenure.domain.wishlist.enums.WishlistTab;
import com.tenure.domain.wishlist.service.WishlistService;
import com.tenure.global.pagination.CursorCodec;
import com.tenure.global.pagination.CursorResponse;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wishlists")
@RequiredArgsConstructor
public class WishlistController implements WishlistApi {

	private final WishlistService wishlistService;
	private final CursorCodec cursorCodec;

	// 위시리스트 목록
	@Override
	@GetMapping
	public ResponseEntity<BaseResponse<CursorResponse<WishlistItemResponse>>> getWishlists(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(required = false, defaultValue = "ALL") WishlistTab tab,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		CursorSlice<WishlistItemResponse> slice =
				wishlistService.list(authUser.id(), tab, cursorCodec.decode(cursor), size);
		return ResponseEntity.ok(BaseResponse.success(cursorCodec.toResponse(slice)));
	}

	// 위시 알림 on/off
	@Override
	@PatchMapping("/{wishlistId}/alarm")
	public ResponseEntity<BaseResponse<Void>> updateAlarm(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long wishlistId,
			@RequestBody WishlistAlarmUpdateRequest request
	) {
		wishlistService.updateAlarm(authUser.id(), wishlistId, request.getAlarmEnabled());
		return ResponseEntity.ok(BaseResponse.success());
	}
}
