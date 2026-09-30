package com.tenure.domain.item.dto.response;

import com.tenure.domain.item.enums.RecommendationBlock;
import com.tenure.domain.post.dto.response.PostSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 아이템 기준 추천 블록 하나. 카드 형식은 피드와 같다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "추천 블록")
public class ItemRecommendationResponse {

	@Schema(description = "블록 종류", example = "SIMILAR_MOOD")
	private final RecommendationBlock block;

	@Schema(description = "화면에 그대로 쓰는 제목", example = "비슷한 무드 모아보기")
	private final String title;

	@Schema(description = "부제", example = "지금 본 옷차림과 어울리는 스타일을 추천해요.")
	private final String description;

	@Schema(description = "게시물 카드")
	private final List<PostSummaryResponse> posts;
}
