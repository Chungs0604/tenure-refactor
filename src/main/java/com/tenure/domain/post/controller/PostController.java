package com.tenure.domain.post.controller;

import com.tenure.domain.post.dto.request.PostCreateRequest;
import com.tenure.domain.post.dto.request.PostUpdateRequest;
import com.tenure.domain.post.dto.response.PostCreateResponse;
import com.tenure.domain.post.dto.response.PostIdResponse;
import com.tenure.domain.post.dto.response.PostLikeResponse;
import com.tenure.domain.post.dto.response.PostResponse;
import com.tenure.domain.post.dto.response.PostSaveResponse;
import com.tenure.domain.post.dto.response.PostSummaryResponse;
import com.tenure.domain.post.enums.FeedTab;
import com.tenure.domain.post.service.PostQueryService;
import com.tenure.domain.post.service.PostService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController implements PostApi {

	private final PostService postService;
	private final PostQueryService postQueryService;

	/** 커서를 문자열로 주고받는 일은 전송 형식이라 여기서 처리한다 — Service 는 정렬 키만 안다. */
	private final CursorCodec cursorCodec;

	// 홈 피드 (모두 / 팔로우 탭)
	@Override
	@GetMapping
	public ResponseEntity<BaseResponse<CursorResponse<PostSummaryResponse>>> getFeed(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(defaultValue = "ALL") FeedTab tab,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		CursorSlice<PostSummaryResponse> slice =
				postQueryService.getFeed(authUser.id(), tab, cursorCodec.decode(cursor), size);

		CursorResponse<PostSummaryResponse> response = cursorCodec.toResponse(slice);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// OOTD 상세
	@Override
	@GetMapping("/{postId}")
	public ResponseEntity<BaseResponse<PostResponse>> getPost(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long postId
	) {
		PostResponse response = postQueryService.getPost(authUser.id(), postId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// OOTD 게시
	@Override
	@PostMapping
	public ResponseEntity<BaseResponse<PostCreateResponse>> create(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestBody PostCreateRequest request
	) {
		PostCreateResponse response = postService.create(authUser.id(), request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 게시물 태그 수정
	@Override
	@PatchMapping("/{postId}")
	public ResponseEntity<BaseResponse<PostIdResponse>> updateTags(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long postId,
			@RequestBody PostUpdateRequest request
	) {
		PostIdResponse response = postService.updateTags(authUser.id(), postId, request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 게시물 삭제 (soft)
	@Override
	@DeleteMapping("/{postId}")
	public ResponseEntity<BaseResponse<Void>> delete(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long postId
	) {
		postService.delete(authUser.id(), postId);
		return ResponseEntity.ok(BaseResponse.success());
	}

	// 좋아요
	@Override
	@PostMapping("/{postId}/like")
	public ResponseEntity<BaseResponse<PostLikeResponse>> like(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long postId
	) {
		PostLikeResponse response = postService.like(authUser.id(), postId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 좋아요 취소
	@Override
	@DeleteMapping("/{postId}/like")
	public ResponseEntity<BaseResponse<PostLikeResponse>> unlike(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long postId
	) {
		PostLikeResponse response = postService.unlike(authUser.id(), postId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 저장
	@Override
	@PostMapping("/{postId}/save")
	public ResponseEntity<BaseResponse<PostSaveResponse>> save(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long postId
	) {
		PostSaveResponse response = postService.save(authUser.id(), postId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 저장 취소
	@Override
	@DeleteMapping("/{postId}/save")
	public ResponseEntity<BaseResponse<PostSaveResponse>> unsave(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long postId
	) {
		PostSaveResponse response = postService.unsave(authUser.id(), postId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}
}
