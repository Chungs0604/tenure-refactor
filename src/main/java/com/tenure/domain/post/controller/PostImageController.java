package com.tenure.domain.post.controller;

import com.tenure.domain.post.dto.response.TaggedItemResponse;
import com.tenure.domain.post.service.PostQueryService;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/post-images")
@RequiredArgsConstructor
public class PostImageController implements PostImageApi {

	private final PostQueryService postQueryService;

	// 사진 속 태그된 아이템 목록
	@Override
	@GetMapping("/{imageId}/tagged-items")
	public ResponseEntity<BaseResponse<List<TaggedItemResponse>>> getTaggedItems(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long imageId
	) {
		List<TaggedItemResponse> response = postQueryService.getTaggedItems(authUser.id(), imageId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}
}
