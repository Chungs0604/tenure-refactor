package com.tenure.domain.post.controller;

import com.tenure.domain.post.dto.response.MyPostSummaryResponse;
import com.tenure.domain.post.dto.response.UserPostSummaryResponse;
import com.tenure.domain.post.enums.MyPostTab;
import com.tenure.domain.post.service.PostQueryService;
import com.tenure.global.pagination.CursorCodec;
import com.tenure.global.pagination.CursorResponse;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserPostController implements UserPostApi {

	private final PostQueryService postQueryService;

	/** 커서를 문자열로 주고받는 일은 전송 형식이라 여기서 처리한다 — Service 는 정렬 키만 안다. */
	private final CursorCodec cursorCodec;

	// 내 게시물 (게시물 / 좋아요 / 저장 탭)
	@Override
	@GetMapping("/me/posts")
	public ResponseEntity<BaseResponse<CursorResponse<MyPostSummaryResponse>>> getMyPosts(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(defaultValue = "POSTS") MyPostTab tab,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth yearMonth,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		CursorSlice<MyPostSummaryResponse> slice = postQueryService
				.getMyPosts(authUser.id(), tab, yearMonth, cursorCodec.decode(cursor), size);

		CursorResponse<MyPostSummaryResponse> response = cursorCodec.toResponse(slice);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 타인 게시물 목록
	@Override
	@GetMapping("/{userId}/posts")
	public ResponseEntity<BaseResponse<CursorResponse<UserPostSummaryResponse>>> getUserPosts(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long userId,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		CursorSlice<UserPostSummaryResponse> slice = postQueryService
				.getUserPosts(authUser.id(), userId, cursorCodec.decode(cursor), size);

		CursorResponse<UserPostSummaryResponse> response = cursorCodec.toResponse(slice);
		return ResponseEntity.ok(BaseResponse.success(response));
	}
}
