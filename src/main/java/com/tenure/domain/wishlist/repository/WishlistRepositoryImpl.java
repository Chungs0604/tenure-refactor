package com.tenure.domain.wishlist.repository;

import static com.tenure.domain.order.entity.QTradeOrder.tradeOrder;
import static com.tenure.domain.wishlist.entity.QWishlist.wishlist;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.wishlist.entity.Wishlist;
import com.tenure.domain.wishlist.enums.WishlistTab;
import com.tenure.global.pagination.Cursor;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WishlistRepositoryImpl implements WishlistRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<Wishlist> findAllForList(Long userId, WishlistTab tab, Cursor cursor, int size) {
		return queryFactory
				.selectFrom(wishlist)
				// 목록 행 조립에 바로 쓰는 값이라 지연 로딩 추가 쿼리 없이 한 번에 읽음
				.join(wishlist.item).fetchJoin()
				.where(
						wishlist.user.id.eq(userId),
						wishlist.item.deletedAt.isNull(),
						tabEq(tab),
						afterCreatedAt(cursor))
				.orderBy(wishlist.createdAt.desc(), wishlist.id.desc())
				// size + 1 건을 읽어서 다음 페이지 유무를 판정함(Service 의 CursorSlice 가 자름)
				.limit(size + 1L)
				.fetch();
	}

	private BooleanExpression tabEq(WishlistTab tab) {
		return switch (tab) {
			case ALL -> null;
			case ON_SALE -> wishlist.item.saleStatus.eq(SaleStatus.ON_SALE);
			case IN_TRADE -> JPAExpressions.selectOne()
					.from(tradeOrder)
					.where(
							tradeOrder.item.eq(wishlist.item),
							tradeOrder.status.notIn(OrderStatus.FINISHED))
					.exists();
		};
	}

	/** 정렬이 (createdAt desc, id desc) 라 조건도 같은 순서로 비교함 */
	private BooleanExpression afterCreatedAt(Cursor cursor) {
		if (cursor == null) {
			return null;
		}

		LocalDateTime createdAt = cursor.asDateTime();
		return wishlist.createdAt.lt(createdAt)
				.or(wishlist.createdAt.eq(createdAt).and(wishlist.id.lt(cursor.getId())));
	}
}
