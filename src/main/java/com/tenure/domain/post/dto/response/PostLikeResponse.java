package com.tenure.domain.post.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 좋아요·취소 결과. 화면이 숫자를 바로 갈아끼울 수 있게 갱신값을 돌려준다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "좋아요 수")
public class PostLikeResponse {

	@Schema(description = "갱신된 좋아요 수", example = "1234")
	private final Integer likeCount;
}
