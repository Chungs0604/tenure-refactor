package com.tenure.domain.search.enums;

/** 추천 섹션 더보기(API-207)의 경로 변수. {@code POPULAR_USER} 만 응답이 사용자 목록이다. */
public enum RecommendationSection {

	/** Redis recent-view 의 최근 게시물과 태그 카테고리가 겹치는 것 (DEC-014) */
	SIMILAR_TO_RECENT,
	/** 기간 내 like_count 상위 */
	POPULAR_STYLE,
	/** created_at desc */
	NEW_OOTD,
	/** follower_count 상위. 응답이 users[] */
	POPULAR_USER;

}
