package com.tenure.domain.order.repository;

import static com.tenure.domain.order.entity.QTradeOrder.tradeOrder;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.global.pagination.Cursor;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TradeOrderRepositoryImpl implements TradeOrderRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<TradeOrder> findAllBySellerForFeed(
			Long sellerId, Collection<OrderStatus> statuses, Cursor cursor, int size
	) {
		return queryFactory
				.selectFrom(tradeOrder)
				.join(tradeOrder.item).fetchJoin()
				.join(tradeOrder.buyer).fetchJoin()
				.where(
						tradeOrder.seller.id.eq(sellerId),
						inStatuses(statuses),
						afterCreatedAt(cursor))
				.orderBy(tradeOrder.createdAt.desc(), tradeOrder.id.desc())
				.limit(size + 1L)
				.fetch();
	}

	@Override
	public List<TradeOrder> findAllByBuyerForFeed(
			Long buyerId, Collection<OrderStatus> statuses, Cursor cursor, int size
	) {
		return queryFactory
				.selectFrom(tradeOrder)
				.join(tradeOrder.item).fetchJoin()
				.join(tradeOrder.seller).fetchJoin()
				.where(
						tradeOrder.buyer.id.eq(buyerId),
						inStatuses(statuses),
						afterCreatedAt(cursor))
				.orderBy(tradeOrder.createdAt.desc(), tradeOrder.id.desc())
				.limit(size + 1L)
				.fetch();
	}

	/** null 이면 전체 탭 — 상태 조건 없음 */
	private BooleanExpression inStatuses(Collection<OrderStatus> statuses) {
		return statuses == null ? null : tradeOrder.status.in(statuses);
	}

	/** 생성일 커서. 정렬이 (createdAt desc, id desc) 라 조건도 같은 순서로 비교한다 */
	private BooleanExpression afterCreatedAt(Cursor cursor) {
		if (cursor == null) {
			return null;
		}

		LocalDateTime createdAt = cursor.asDateTime();
		return tradeOrder.createdAt.lt(createdAt)
				.or(tradeOrder.createdAt.eq(createdAt).and(tradeOrder.id.lt(cursor.getId())));
	}
}
