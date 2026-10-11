package com.tenure.domain.search.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * API-200 전용 목록 응답. 공용 {@code CursorResponse} 에 {@code totalCount} 를 얹지 않는다 —
 * 그 클래스 설계 의도(목록 화면은 전부 무한 스크롤이라 총계가 없다)를 그대로 지키기 위해,
 * 총계가 필요한 이 화면만 따로 둔다({@code DEC-022}).
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "게시물 검색 결과")
public class SearchPostListResponse {

	private final List<SearchPostSummaryResponse> content;

	private final String nextCursor;

	private final Boolean hasNext;

	@Schema(description = "총 게시물 수. 검색 결과에만 있다", example = "80")
	private final Integer totalCount;
}
