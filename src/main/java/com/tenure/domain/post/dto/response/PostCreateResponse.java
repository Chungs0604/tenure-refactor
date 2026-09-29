package com.tenure.domain.post.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 게시 결과.
 *
 * 승격 여부를 함께 준다 — 20개째 게시로 레코드가 된 사용자에게 그 사실을 바로 알려야 판매 전환
 * 화면이 왜 달라졌는지 설명된다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "게시 결과")
public class PostCreateResponse {

	@Schema(description = "게시물 id")
	private final Long id;

	@Schema(description = "이 게시로 레코드 등급이 됐으면 true")
	private final Boolean gradePromoted;
}
