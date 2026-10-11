package com.tenure.domain.search.dto.response;

import com.tenure.domain.post.enums.AspectRatio;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 검색 결과·추천 섹션의 게시물 썸네일. 좋아요·저장 여부를 싣지 않는다 — API-200/202/207
 * 명세 응답에 그 필드가 없다(그리드 카드마다 상태를 조회하지 않아도 되는 이유이기도 하다).
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "검색 결과 게시물 썸네일")
public class SearchPostSummaryResponse {

	@Schema(description = "게시물 id")
	private final Long id;

	@Schema(description = "대표 사진 URL")
	private final String imageUrl;

	@Schema(description = "촬영 비율. 카드 높이 계산에 쓴다", example = "RATIO_3_4")
	private final AspectRatio aspectRatio;
}
