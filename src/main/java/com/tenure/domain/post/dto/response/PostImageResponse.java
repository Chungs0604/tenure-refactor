package com.tenure.domain.post.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 상세의 사진 한 장. 태그된 아이템 목록 조회의 키(id)가 여기서 나온다 (DEC-104). */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "게시물 사진")
public class PostImageResponse {

	@Schema(description = "사진 id. 태그된 아이템 목록 조회에 쓴다")
	private final Long id;

	@Schema(description = "사진 URL")
	private final String url;

	@Schema(description = "대표 사진이면 true")
	private final Boolean isCover;

	@Schema(description = "이 사진에 붙은 태그")
	private final List<PostTagResponse> tags;
}
