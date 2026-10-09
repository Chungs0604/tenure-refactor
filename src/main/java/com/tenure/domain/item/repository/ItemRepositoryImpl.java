package com.tenure.domain.item.repository;

import static com.tenure.domain.item.entity.QItem.item;
import static com.tenure.domain.item.entity.QItemOwnership.itemOwnership;
import static com.tenure.domain.post.entity.QPost.post;
import static com.tenure.domain.post.entity.QPostItemTag.postItemTag;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tenure.domain.block.repository.BlockPredicates;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.QItem;
import com.tenure.domain.item.enums.RecommendationBlock;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.entity.QPostItemTag;
import com.tenure.global.pagination.Cursor;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 동적 조건은 BooleanExpression 을 돌려주는 메서드로 나누고 where 에 나열한다.
 * 조건이 없을 때 null 을 돌려주면 where 가 그 자리를 무시한다 — PostRepositoryImpl 과 같은 규약이다.
 */
@RequiredArgsConstructor
public class ItemRepositoryImpl implements ItemRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<Item> findOwnedItems(Long ownerId, String keyword, Cursor cursor, int size) {

		return queryFactory
				.selectFrom(item)
				.where(
						item.deletedAt.isNull(),
						ownedBy(ownerId),
						matches(keyword),
						afterCreatedAt(cursor))
				.orderBy(item.createdAt.desc(), item.id.desc())
				.limit(size + 1L)
				.fetch();
	}

	@Override
	public List<Post> findRecommendedPosts(
			Long viewerId, Item item, RecommendationBlock block, int limit
	) {
		return queryFactory
				.selectFrom(post)
				.where(
						post.deletedAt.isNull(),
						notBlocked(viewerId),
						taggedWithCandidates(item, block))
				.orderBy(post.createdAt.desc(), post.id.desc())
				.limit(limit)
				.fetch();
	}

	/**
	 * 후보 아이템이 태그된 게시물만 남긴다.
	 *
	 * 태그는 사진 단위라 한 게시물에 여러 번 걸릴 수 있다 — exists 로 판정해 게시물이
	 * 중복되지 않게 한다. distinct 를 쓰면 정렬·limit 이 함께 흔들린다.
	 */
	private BooleanExpression taggedWithCandidates(Item base, RecommendationBlock block) {
		QItem candidate = new QItem("candidate");

		BooleanExpression rule = switch (block) {
			// 대분류가 같은 다른 아이템
			case SIMILAR_MOOD -> candidate.category.parent.id.eq(base.getCategory().getParent().getId());
			// 세부 카테고리가 같은 다른 아이템
			case SIMILAR_ITEM -> candidate.category.id.eq(base.getCategory().getId());
			// 이 아이템과 같은 게시물에 함께 태그됐던 아이템
			case GOOD_COORDI -> candidate.id.in(coTaggedItemIds(base.getId()));
		};

		return JPAExpressions
				.selectOne()
				.from(postItemTag)
				.join(candidate).on(candidate.id.eq(postItemTag.item.id))
				.where(
						postItemTag.postImage.post.id.eq(post.id),
						candidate.id.ne(base.getId()),
						candidate.deletedAt.isNull(),
						rule)
				.exists();
	}

	/** 기준 아이템과 같은 게시물에 태그됐던 아이템들. */
	private JPQLQuery<Long> coTaggedItemIds(Long itemId) {
		QPostItemTag mine = new QPostItemTag("mine");
		QPostItemTag together = new QPostItemTag("together");

		return JPAExpressions
				.select(together.item.id)
				.from(mine)
				.join(together).on(together.postImage.post.id.eq(mine.postImage.post.id))
				.where(mine.item.id.eq(itemId), together.item.id.ne(itemId));
	}

	/** 내가 차단한 쪽과 나를 차단한 쪽을 모두 뺀다 — PostRepositoryImpl 과 같은 규칙이다. */
	private BooleanExpression notBlocked(Long viewerId) {
		return BlockPredicates.excludeBlockedEitherWay(post.author.id, viewerId);
	}

	/**
	 * 현재 소유 구간이 이 사용자로 열려 있는 아이템.
	 *
	 * 조인 대신 exists 를 쓴다 — 열린 구간은 아이템당 하나뿐이라 조인해도 행이 늘지는 않지만,
	 * 소유 구간의 어떤 값도 결과에 쓰지 않으므로 존재 판정이면 충분하다.
	 */
	private BooleanExpression ownedBy(Long ownerId) {
		return JPAExpressions
				.selectOne()
				.from(itemOwnership)
				.where(
						itemOwnership.item.id.eq(item.id),
						itemOwnership.owner.id.eq(ownerId),
						itemOwnership.endDate.isNull())
				.exists();
	}

	/**
	 * 브랜드명·아이템명 부분 일치. 소유자 기준 목록이라 ix_item_registrant 가 아니라
	 * 소유 구간 조건이 먼저 걸러 준다 — 한 사람의 아이템은 많아야 수백 건이다.
	 */
	private BooleanExpression matches(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}

		String trimmed = keyword.trim();
		return item.brandName.containsIgnoreCase(trimmed)
				.or(item.name.containsIgnoreCase(trimmed));
	}

	/**
	 * 등록일 커서. 정렬이 (createdAt desc, id desc) 라 조건도 같은 순서로 비교한다.
	 *
	 * createdAt < c 만 쓰면 같은 시각에 등록한 아이템을 건너뛴다.
	 */
	private BooleanExpression afterCreatedAt(Cursor cursor) {
		if (cursor == null) {
			return null;
		}

		LocalDateTime createdAt = cursor.asDateTime();
		return item.createdAt.lt(createdAt)
				.or(item.createdAt.eq(createdAt).and(item.id.lt(cursor.getId())));
	}
}
