package com.tenure.domain.search.repository;

import static com.tenure.domain.user.entity.QUser.user;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tenure.domain.block.repository.BlockPredicates;
import com.tenure.domain.user.entity.User;
import com.tenure.global.pagination.Cursor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 계정 검색(API-201). 닉네임 부분 일치만 있고 명세에 정렬 선택지가 없어 팔로워 수 순으로
 * 고정했다 — 추천 섹션의 '인기 사용자'(follower_count 상위)와 기준을 맞춘 것이다.
 *
 * {@code JpaRepository} 의 Custom 조각이 아니라 독립 빈이다 — 한 엔티티에 종속되지 않는
 * 도메인 간 조회라 끼워 넣을 자연스러운 Repository 가 없다. {@code @Repository} 로 직접 등록한다.
 */
@Repository
@RequiredArgsConstructor
public class UserSearchRepositoryImpl implements UserSearchRepository {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<User> searchByNickname(String keyword, Long viewerId, Cursor cursor, int size) {
		return queryFactory
				.selectFrom(user)
				.where(
						user.nickname.containsIgnoreCase(keyword.trim()),
						BlockPredicates.excludeBlockedEitherWay(user.id, viewerId),
						afterFollowerCount(cursor))
				.orderBy(user.followerCount.desc(), user.id.desc())
				.limit(size + 1L)
				.fetch();
	}

	private BooleanExpression afterFollowerCount(Cursor cursor) {
		if (cursor == null) {
			return null;
		}
		Integer value = cursor.asInt();
		return user.followerCount.lt(value)
				.or(user.followerCount.eq(value).and(user.id.lt(cursor.getId())));
	}
}
