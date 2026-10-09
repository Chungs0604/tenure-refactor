package com.tenure.domain.post.repository;

import static com.tenure.domain.follow.entity.QFollow.follow;
import static com.tenure.domain.post.entity.QPost.post;
import static com.tenure.domain.post.entity.QPostLike.postLike;
import static com.tenure.domain.post.entity.QPostSave.postSave;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tenure.domain.block.repository.BlockPredicates;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.enums.FeedTab;
import com.tenure.domain.post.enums.MyPostTab;
import com.tenure.global.pagination.Cursor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 동적 조건은 BooleanExpression 을 돌려주는 메서드로 나누고 where 에 나열한다.
 * 조건이 없을 때 null 을 돌려주면 where 가 그 자리를 무시하므로, 분기를 조립
 * 코드가 아니라 조건 메서드 안에 둘 수 있다 — 쿼리 본문이 '무엇을 거는지' 목록으로 읽힌다.
 */
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<Post> findFeed(Long viewerId, FeedTab tab, Cursor cursor, int size) {

		// 카드에 작성자를 그리지 않으므로 author 를 함께 읽지 않는다. 차단 제외는 FK 값만 보면 된다
		return queryFactory
				.selectFrom(post)
				.where(
						post.deletedAt.isNull(),
						notBlocked(viewerId),
						followedBy(viewerId, tab),
						afterCreatedAt(cursor))
				.orderBy(post.createdAt.desc(), post.id.desc())
				.limit(size + 1L)
				.fetch();
	}

	@Override
	public List<Post> findMyPosts(
			Long userId, MyPostTab tab, YearMonth yearMonth, Cursor cursor, int size
	) {
		return queryFactory
				.selectFrom(post)
				.where(
						post.deletedAt.isNull(),
						tabCondition(userId, tab),
						wornIn(yearMonth),
						afterWornDate(cursor))
				.orderBy(post.wornDate.desc(), post.id.desc())
				.limit(size + 1L)
				.fetch();
	}

	@Override
	public List<Post> findUserPosts(Long authorId, Cursor cursor, int size) {

		return queryFactory
				.selectFrom(post)
				.where(
						post.deletedAt.isNull(),
						post.author.id.eq(authorId),
						afterWornDate(cursor))
				.orderBy(post.wornDate.desc(), post.id.desc())
				.limit(size + 1L)
				.fetch();
	}

	/**
	 * 게시일 커서. 정렬이 (createdAt desc, id desc) 라 조건도 같은 순서로 비교한다.
	 *
	 * createdAt < c 만 쓰면 같은 시각의 게시물을 건너뛴다 — 한 초에 여러 건이 올라오는
	 * 목록에서 실제로 새는 지점이다. 같은 시각이면 id 로 가른다.
	 */
	private BooleanExpression afterCreatedAt(Cursor cursor) {
		if (cursor == null) {
			return null;
		}

		LocalDateTime createdAt = cursor.asDateTime();
		return post.createdAt.lt(createdAt)
				.or(post.createdAt.eq(createdAt).and(post.id.lt(cursor.getId())));
	}

	/** 착용일 커서. 착용일은 날짜라 같은 값이 훨씬 잦아 id tie-break 가 더 중요하다. */
	private BooleanExpression afterWornDate(Cursor cursor) {
		if (cursor == null) {
			return null;
		}

		LocalDate wornDate = cursor.asDate();
		return post.wornDate.lt(wornDate)
				.or(post.wornDate.eq(wornDate).and(post.id.lt(cursor.getId())));
	}

	/**
	 * 차단 관계를 양방향으로 제외한다.
	 *
	 * 내가 차단한 사람의 게시물이 안 보이는 것은 당연하고, 나를 차단한 사람의 게시물도 내리는 게
	 * 맞다 — 차단은 '서로 마주치지 않게' 하는 조치이므로 한쪽만 막으면 절반만 지켜진다.
	 */
	private BooleanExpression notBlocked(Long viewerId) {
		return BlockPredicates.excludeBlockedEitherWay(post.author.id, viewerId);
	}

	/** 팔로우 탭에서만 건다. 모두 탭이면 조건이 없다. */
	private BooleanExpression followedBy(Long viewerId, FeedTab tab) {
		return tab == FeedTab.FOLLOWING
				? post.author.id.in(
						JPAExpressions.select(follow.following.id)
								.from(follow)
								.where(follow.follower.id.eq(viewerId)))
				: null;
	}

	/** 탭마다 '누구의 게시물인가' 가 달라진다 — 게시물 탭만 작성자 기준이고 나머지는 반응 기준이다. */
	private BooleanExpression tabCondition(Long userId, MyPostTab tab) {
		return switch (tab) {
			case LIKED -> post.id.in(
					JPAExpressions.select(postLike.post.id)
							.from(postLike)
							.where(postLike.user.id.eq(userId)));
			case SAVED -> post.id.in(
					JPAExpressions.select(postSave.post.id)
							.from(postSave)
							.where(postSave.user.id.eq(userId)));
			case POSTS -> post.author.id.eq(userId);
		};
	}

	/** 월 헤더로 묶는 화면에서 특정 달만 볼 때 쓴다. 고르지 않으면 조건이 없다. */
	private BooleanExpression wornIn(YearMonth yearMonth) {
		return yearMonth == null
				? null
				: post.wornDate.between(yearMonth.atDay(1), yearMonth.atEndOfMonth());
	}
}
