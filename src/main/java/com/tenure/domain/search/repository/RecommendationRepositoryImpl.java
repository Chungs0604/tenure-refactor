package com.tenure.domain.search.repository;

import static com.tenure.domain.post.entity.QPost.post;
import static com.tenure.domain.post.entity.QPostItemTag.postItemTag;
import static com.tenure.domain.user.entity.QUser.user;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tenure.domain.block.repository.BlockPredicates;
import com.tenure.domain.item.entity.QItem;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.user.entity.User;
import com.tenure.global.pagination.Cursor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 추천 4블록의 공통 조회. {@code JpaRepository} 조각이 아니라 독립 빈이다 — PostSearchRepository
 * 와 같은 이유(엔티티 하나에 종속되지 않음).
 */
@Repository
@RequiredArgsConstructor
public class RecommendationRepositoryImpl implements RecommendationRepository {

	/** '최근 기간' 이 명세에 정해진 값이 아니다 — 인기 스타일이 1년 전 글로 고정되지 않을 정도로 잡았다. */
	private static final int POPULAR_STYLE_WINDOW_DAYS = 30;

	private final JPAQueryFactory queryFactory;

	@Override
	public List<Post> findSimilarToRecent(List<Long> recentPostIds, Long viewerId, Cursor cursor, int size) {
		if (recentPostIds.isEmpty()) {
			return List.of();
		}

		QItem candidate = new QItem("candidate");

		return queryFactory
				.selectFrom(post)
				.where(
						post.deletedAt.isNull(),
						post.id.notIn(recentPostIds),
						BlockPredicates.excludeBlockedEitherWay(post.author.id, viewerId),
						sharesCategoryWith(candidate, recentPostIds),
						afterRankScore(cursor))
				.orderBy(post.rankScore.desc(), post.id.desc())
				.limit(size + 1L)
				.fetch();
	}

	@Override
	public List<Post> findPopularStyle(Long viewerId, Cursor cursor, int size) {
		LocalDateTime since = LocalDateTime.now().minusDays(POPULAR_STYLE_WINDOW_DAYS);

		return queryFactory
				.selectFrom(post)
				.where(
						post.deletedAt.isNull(),
						post.createdAt.goe(since),
						BlockPredicates.excludeBlockedEitherWay(post.author.id, viewerId),
						afterLikeCount(cursor))
				.orderBy(post.likeCount.desc(), post.id.desc())
				.limit(size + 1L)
				.fetch();
	}

	@Override
	public List<Post> findNewOotd(Long viewerId, Cursor cursor, int size) {
		return queryFactory
				.selectFrom(post)
				.where(
						post.deletedAt.isNull(),
						BlockPredicates.excludeBlockedEitherWay(post.author.id, viewerId),
						afterCreatedAt(cursor))
				.orderBy(post.createdAt.desc(), post.id.desc())
				.limit(size + 1L)
				.fetch();
	}

	@Override
	public List<User> findPopularUsers(Long viewerId, Cursor cursor, int size) {
		return queryFactory
				.selectFrom(user)
				.where(
						BlockPredicates.excludeBlockedEitherWay(user.id, viewerId),
						afterFollowerCount(cursor))
				.orderBy(user.followerCount.desc(), user.id.desc())
				.limit(size + 1L)
				.fetch();
	}

	/**
	 * 후보 게시물에 태그된 아이템의 카테고리가, 씨드 게시물들에 태그된 아이템의 카테고리와
	 * 하나라도 겹치는가. 씨드가 여럿이라 서브쿼리를 둘로 나눈다 — 후보의 카테고리 집합과
	 * 씨드의 카테고리 집합의 교집합을 직접 구하는 대신, '씨드 카테고리 중 하나에 속하는
	 * 아이템이 이 게시물에 태그돼 있는가' 로 뒤집어 묻는다.
	 */
	private BooleanExpression sharesCategoryWith(QItem candidate, List<Long> seedPostIds) {
		return JPAExpressions
				.selectOne()
				.from(postItemTag)
				.join(candidate).on(candidate.id.eq(postItemTag.item.id))
				.where(
						postItemTag.postImage.post.id.eq(post.id),
						candidate.deletedAt.isNull(),
						candidate.category.id.in(seedCategoryIds(seedPostIds)))
				.exists();
	}

	private JPQLQuery<Long> seedCategoryIds(List<Long> seedPostIds) {
		QItem seedItem = new QItem("seedItem");
		return JPAExpressions
				.select(seedItem.category.id)
				.from(postItemTag)
				.join(seedItem).on(seedItem.id.eq(postItemTag.item.id))
				.where(postItemTag.postImage.post.id.in(seedPostIds));
	}

	private BooleanExpression afterRankScore(Cursor cursor) {
		if (cursor == null) {
			return null;
		}
		BigDecimal value = cursor.asBigDecimal();
		return post.rankScore.lt(value).or(post.rankScore.eq(value).and(post.id.lt(cursor.getId())));
	}

	private BooleanExpression afterLikeCount(Cursor cursor) {
		if (cursor == null) {
			return null;
		}
		Integer value = cursor.asInt();
		return post.likeCount.lt(value).or(post.likeCount.eq(value).and(post.id.lt(cursor.getId())));
	}

	private BooleanExpression afterCreatedAt(Cursor cursor) {
		if (cursor == null) {
			return null;
		}
		LocalDateTime value = cursor.asDateTime();
		return post.createdAt.lt(value).or(post.createdAt.eq(value).and(post.id.lt(cursor.getId())));
	}

	private BooleanExpression afterFollowerCount(Cursor cursor) {
		if (cursor == null) {
			return null;
		}
		Integer value = cursor.asInt();
		return user.followerCount.lt(value).or(user.followerCount.eq(value).and(user.id.lt(cursor.getId())));
	}
}
