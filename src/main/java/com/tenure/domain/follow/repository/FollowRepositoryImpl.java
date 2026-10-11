package com.tenure.domain.follow.repository;

import static com.tenure.domain.follow.entity.QFollow.follow;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tenure.domain.follow.entity.Follow;
import com.tenure.global.pagination.Cursor;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FollowRepositoryImpl implements FollowRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<Follow> findFollowers(Long userId, Cursor cursor, int size) {
		return queryFactory
				.selectFrom(follow)
				// 목록 행 조립에 바로 쓰는 값이라 지연 로딩 추가 쿼리 없이 한 번에 읽음
				.join(follow.follower).fetchJoin()
				.where(follow.following.id.eq(userId), afterCreatedAt(cursor))
				.orderBy(follow.createdAt.desc(), follow.id.desc())
				// size + 1 건을 읽어서 다음 페이지 유무를 판정함(Service 의 CursorSlice 가 자름)
				.limit(size + 1L)
				.fetch();
	}

	@Override
	public List<Follow> findFollowings(Long userId, Cursor cursor, int size) {
		return queryFactory
				.selectFrom(follow)
				.join(follow.following).fetchJoin()
				.where(follow.follower.id.eq(userId), afterCreatedAt(cursor))
				.orderBy(follow.createdAt.desc(), follow.id.desc())
				.limit(size + 1L)
				.fetch();
	}

	/** 정렬이 (createdAt desc, id desc) 라 조건도 같은 순서로 비교함 */
	private BooleanExpression afterCreatedAt(Cursor cursor) {
		if (cursor == null) {
			return null;
		}

		LocalDateTime createdAt = cursor.asDateTime();
		return follow.createdAt.lt(createdAt)
				.or(follow.createdAt.eq(createdAt).and(follow.id.lt(cursor.getId())));
	}
}
