package com.tenure.domain.search.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** API-206. 검색창 포커스 시 뜨는 추천 검색어 칩 한 개. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "추천 검색어")
public class SuggestedKeywordResponse {

	@Schema(description = "탭하면 이 값으로 API-200/201 을 호출한다", example = "데님 팬츠")
	private final String keyword;
}
