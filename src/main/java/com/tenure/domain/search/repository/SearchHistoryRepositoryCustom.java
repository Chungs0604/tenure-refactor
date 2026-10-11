package com.tenure.domain.search.repository;

import com.tenure.domain.search.entity.SearchHistory;
import java.time.LocalDateTime;
import java.util.List;

public interface SearchHistoryRepositoryCustom {

	/** API-203. 최근 검색어. 중복 검색도 그대로 쌓이는 기록이라 키워드로 묶지 않는다. */
	List<SearchHistory> findRecent(Long userId, int limit);

	/** API-206. 최근 N일 keyword 별 건수 상위. 배치가 아니라 요청 시점에 집계하고 Redis 로 캐싱한다. */
	List<String> findTrendingKeywords(LocalDateTime since, int limit);
}
