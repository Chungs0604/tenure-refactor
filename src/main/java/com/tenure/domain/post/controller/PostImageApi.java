package com.tenure.domain.post.controller;

import com.tenure.domain.post.dto.response.TaggedItemResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/**
 * 사진 단위 조회. 경로가 게시물이 아니라 사진인 이유는 태그가 사진에 매달리기 때문이다 (DEC-104).
 */
@Validated
@Tag(name = "게시물 사진", description = "사진 속 태그된 아이템 API")
public interface PostImageApi {

	@Operation(summary = "태그된 아이템 목록",
			description = "화면 문구가 '사진 속에서 태그된 것만 모아봅니다' 라 단위가 사진이다. "
					+ "삭제된 아이템은 DELETED 로 내려가고 화면에서 회색 처리된다. "
					+ "listingId 가 null 이면 판매 등록이 한 번도 없어 CTA 버튼을 띄우지 않는다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "404", description = "POST_404_IMAGE — 사진을 찾을 수 없음")
	})
	ResponseEntity<BaseResponse<List<TaggedItemResponse>>> getTaggedItems(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "사진 id. 상세 응답의 images[].id", example = "1") Long imageId);
}
