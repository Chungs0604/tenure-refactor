package com.tenure.domain.search.repository;

import com.tenure.domain.post.entity.Post;
import com.tenure.domain.user.entity.User;
import com.tenure.global.pagination.Cursor;
import java.util.List;

/** 검색 메인 추천 4블록 (API-202 미리보기 / API-207 더보기). */
public interface RecommendationRepository {

	/** 최근 본 게시물들과 태그 카테고리가 겹치는 다른 게시물 (DEC-014). */
	List<Post> findSimilarToRecent(List<Long> recentPostIds, Long viewerId, Cursor cursor, int size);

	/** 최근 기간 내 좋아요 상위. */
	List<Post> findPopularStyle(Long viewerId, Cursor cursor, int size);

	/** 최신 게시물. */
	List<Post> findNewOotd(Long viewerId, Cursor cursor, int size);

	/** 팔로워 수 상위. */
	List<User> findPopularUsers(Long viewerId, Cursor cursor, int size);
}
