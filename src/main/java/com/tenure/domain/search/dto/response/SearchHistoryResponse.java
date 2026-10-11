package com.tenure.domain.search.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** API-203. 최근 검색어 + 최근 본 사용자. 둘 다 저장소가 달라(RDB·Redis) 한 응답에 묶어 조립한다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "최근 검색어·최근 본 사용자")
public class SearchHistoryResponse {

	@Schema(description = "최근 검색어")
	private final List<Keyword> keywords;

	@Schema(description = "최근 본 사용자")
	private final List<RecentUser> recentUsers;

	@Getter
	@Builder
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	@Schema(description = "최근 검색어 한 건")
	public static class Keyword {

		@Schema(description = "search_history id. 개별 삭제(API-204)에 쓴다")
		private final Long id;

		private final String keyword;

		private final LocalDateTime searchedAt;
	}

	@Getter
	@Builder
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	@Schema(description = "최근 본 사용자 한 명")
	public static class RecentUser {

		@Schema(description = "사용자 id. 개별 삭제(API-208)에 쓴다")
		private final Long id;

		private final String nickname;

		private final String profileImageUrl;
	}
}
