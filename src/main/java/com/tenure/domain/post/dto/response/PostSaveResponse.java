package com.tenure.domain.post.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 저장·취소 결과. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "저장 수")
public class PostSaveResponse {

	@Schema(description = "갱신된 저장 수", example = "1234")
	private final Integer saveCount;
}
