package com.tenure.domain.search.dto.response;

import com.tenure.domain.user.dto.response.UserSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** API-202. 검색 메인의 추천 4블록. 섹션마다 페이징이 없다 — 더보기는 API-207이 맡는다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "검색 메인 추천 4블록")
public class RecommendationResponse {

	@Schema(description = "방금 보신 OOTD와 유사한 게시물")
	private final List<SearchPostSummaryResponse> similarPosts;

	@Schema(description = "지금 인기 있는 스타일")
	private final List<SearchPostSummaryResponse> popularPosts;

	@Schema(description = "새로 올라온 OOTD")
	private final List<SearchPostSummaryResponse> latestPosts;

	/** API-207 과 같은 타입을 쓴다(UserSummaryResponse) — isFollowing 포함 */
	@Schema(description = "인기 사용자")
	private final List<UserSummaryResponse> popularUsers;
}
