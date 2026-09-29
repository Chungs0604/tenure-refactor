package com.tenure.domain.post.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 수정 결과. 화면은 뒤이어 상세를 다시 조회한다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "게시물 id")
public class PostIdResponse {

	@Schema(description = "게시물 id")
	private final Long id;
}
