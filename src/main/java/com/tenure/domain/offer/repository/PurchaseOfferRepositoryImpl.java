package com.tenure.domain.offer.repository;

import static com.tenure.domain.offer.entity.QPurchaseOffer.purchaseOffer;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.global.pagination.Cursor;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PurchaseOfferRepositoryImpl implements PurchaseOfferRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<PurchaseOffer> findAllByBuyerForFeed(Long buyerId, Cursor cursor, int size) {
		return queryFactory
				.selectFrom(purchaseOffer)
				.join(purchaseOffer.item).fetchJoin()
				.join(purchaseOffer.seller).fetchJoin()
				.where(
						purchaseOffer.buyer.id.eq(buyerId),
						afterCreatedAt(cursor))
				.orderBy(purchaseOffer.createdAt.desc(), purchaseOffer.id.desc())
				.limit(size + 1L)
				.fetch();
	}

	/** 생성일 커서. 정렬이 (createdAt desc, id desc) 라 조건도 같은 순서로 비교한다 */
	private BooleanExpression afterCreatedAt(Cursor cursor) {
		if (cursor == null) {
			return null;
		}

		LocalDateTime createdAt = cursor.asDateTime();
		return purchaseOffer.createdAt.lt(createdAt)
				.or(purchaseOffer.createdAt.eq(createdAt).and(purchaseOffer.id.lt(cursor.getId())));
	}
}
