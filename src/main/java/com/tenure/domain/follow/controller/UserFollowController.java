package com.tenure.domain.follow.controller;

import com.tenure.domain.follow.service.FollowService;
import com.tenure.domain.user.dto.response.UserSummaryResponse;
import com.tenure.global.pagination.CursorCodec;
import com.tenure.global.pagination.CursorResponse;
import com.tenure.global.pagination.CursorSlice;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/{userId}")
@RequiredArgsConstructor
public class UserFollowController implements UserFollowApi {

	private final FollowService followService;
	private final CursorCodec cursorCodec;

	// 팔로워 목록
	@Override
	@GetMapping("/followers")
	public ResponseEntity<BaseResponse<CursorResponse<UserSummaryResponse>>> getFollowers(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long userId,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		CursorSlice<UserSummaryResponse> slice =
				followService.listFollowers(authUser.id(), userId, cursorCodec.decode(cursor), size);
		return ResponseEntity.ok(BaseResponse.success(cursorCodec.toResponse(slice)));
	}

	// 팔로잉 목록
	@Override
	@GetMapping("/followings")
	public ResponseEntity<BaseResponse<CursorResponse<UserSummaryResponse>>> getFollowings(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long userId,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		CursorSlice<UserSummaryResponse> slice =
				followService.listFollowings(authUser.id(), userId, cursorCodec.decode(cursor), size);
		return ResponseEntity.ok(BaseResponse.success(cursorCodec.toResponse(slice)));
	}

	// 팔로우
	@Override
	@PostMapping("/follow")
	public ResponseEntity<BaseResponse<Void>> follow(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long userId
	) {
		followService.follow(authUser.id(), userId);
		return ResponseEntity.ok(BaseResponse.success());
	}

	// 언팔로우
	@Override
	@DeleteMapping("/follow")
	public ResponseEntity<BaseResponse<Void>> unfollow(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long userId
	) {
		followService.unfollow(authUser.id(), userId);
		return ResponseEntity.ok(BaseResponse.success());
	}
}
