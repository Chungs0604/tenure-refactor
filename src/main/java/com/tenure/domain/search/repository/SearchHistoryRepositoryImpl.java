package com.tenure.domain.search.repository;

import static com.tenure.domain.search.entity.QSearchHistory.searchHistory;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tenure.domain.search.entity.SearchHistory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SearchHistoryRepositoryImpl implements SearchHistoryRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<SearchHistory> findRecent(Long userId, int limit) {
		return queryFactory
				.selectFrom(searchHistory)
				.where(searchHistory.user.id.eq(userId))
				.orderBy(searchHistory.searchedAt.desc(), searchHistory.id.desc())
				.limit(limit)
				.fetch();
	}

	@Override
	public List<String> findTrendingKeywords(LocalDateTime since, int limit) {
		return queryFactory
				.select(searchHistory.keyword)
				.from(searchHistory)
				.where(searchHistory.searchedAt.goe(since))
				.groupBy(searchHistory.keyword)
				.orderBy(searchHistory.keyword.count().desc())
				.limit(limit)
				.fetch();
	}
}
