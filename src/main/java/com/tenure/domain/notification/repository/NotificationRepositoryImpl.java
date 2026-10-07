package com.tenure.domain.notification.repository;

import static com.tenure.domain.notification.entity.QNotification.notification;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tenure.domain.notification.entity.Notification;
import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.global.pagination.Cursor;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<Notification> findAllForList(Long userId, NotificationCategory category, Cursor cursor, int size) {
		return queryFactory
				.selectFrom(notification)
				.where(
						notification.user.id.eq(userId),
						categoryEq(category),
						afterCreatedAt(cursor))
				.orderBy(notification.createdAt.desc(), notification.id.desc())
				// size + 1 건을 읽어서 다음 페이지 유무를 판정함(Service 의 CursorSlice 가 자름)
				.limit(size + 1L)
				.fetch();
	}

	private BooleanExpression categoryEq(NotificationCategory category) {
		return category == null ? null : notification.category.eq(category);
	}

	/** 정렬이 (createdAt desc, id desc) 라 조건도 같은 순서로 비교함 */
	private BooleanExpression afterCreatedAt(Cursor cursor) {
		if (cursor == null) {
			return null;
		}

		LocalDateTime createdAt = cursor.asDateTime();
		return notification.createdAt.lt(createdAt)
				.or(notification.createdAt.eq(createdAt).and(notification.id.lt(cursor.getId())));
	}
}
