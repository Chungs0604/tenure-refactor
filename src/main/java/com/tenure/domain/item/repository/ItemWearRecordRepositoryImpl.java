package com.tenure.domain.item.repository;

import static com.tenure.domain.item.entity.QItemWearRecord.itemWearRecord;
import static com.tenure.domain.post.entity.QPost.post;
import static com.tenure.domain.post.entity.QPostImage.postImage;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tenure.global.pagination.Cursor;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** 동적 조건은 BooleanExpression 을 돌려주는 메서드로 나눈다 — PostRepositoryImpl 과 같은 규약이다. */
@RequiredArgsConstructor
public class ItemWearRecordRepositoryImpl implements ItemWearRecordRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<Object[]> findHistory(Long itemId, Cursor cursor, int size) {

		List<Tuple> rows = queryFactory
				.select(post.id, postImage.imageKey, post.aspectRatio, itemWearRecord.wornDate)
				.from(itemWearRecord)
				.join(post).on(post.id.eq(itemWearRecord.post.id))
				.join(postImage).on(postImage.post.id.eq(post.id).and(postImage.isCover.isTrue()))
				.where(
						itemWearRecord.item.id.eq(itemId),
						itemWearRecord.voidedAt.isNull(),
						post.deletedAt.isNull(),
						afterWornDate(cursor))
				.orderBy(itemWearRecord.wornDate.desc(), itemWearRecord.id.desc())
				.limit(size + 1L)
				.fetch();

		return rows.stream().map(Tuple::toArray).toList();
	}

	/**
	 * 착용일 커서. 정렬이 (wornDate desc, id desc) 라 조건도 같은 순서로 비교한다.
	 *
	 * 착용일은 날짜라 같은 값이 흔하다 — id tie-break 가 없으면 경계에서 건너뛴다.
	 */
	private BooleanExpression afterWornDate(Cursor cursor) {
		if (cursor == null) {
			return null;
		}

		LocalDate wornDate = cursor.asDate();
		return itemWearRecord.wornDate.lt(wornDate)
				.or(itemWearRecord.wornDate.eq(wornDate).and(itemWearRecord.id.lt(cursor.getId())));
	}
}
