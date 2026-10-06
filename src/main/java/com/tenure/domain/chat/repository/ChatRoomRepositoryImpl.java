package com.tenure.domain.chat.repository;

import static com.tenure.domain.chat.entity.QChatMessage.chatMessage;
import static com.tenure.domain.chat.entity.QChatRoom.chatRoom;
import static com.tenure.domain.chat.entity.QChatRoomMember.chatRoomMember;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tenure.domain.chat.enums.ChatRoomTab;
import com.tenure.global.pagination.Cursor;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ChatRoomRepositoryImpl implements ChatRoomRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<ChatRoomFeedRow> findAllForFeed(
			Long viewerId, ChatRoomTab tab, String keyword, Cursor cursor, int size
	) {
		// 내가 보낸 메시지는 안읽음이 아니다 — sender 제외를 빼면 보내고 나가면 내 배지가 올라간다
		JPQLQuery<Long> unreadCountSubquery = JPAExpressions.select(chatMessage.count())
				.from(chatMessage)
				.where(
						chatMessage.chatRoom.eq(chatRoom),
						chatMessage.sender.id.ne(viewerId),
						chatRoomMember.lastReadMessage.isNull()
								.or(chatMessage.id.gt(chatRoomMember.lastReadMessage.id)));
		NumberExpression<Long> unreadCount = Expressions.asNumber(unreadCountSubquery);

		List<Tuple> rows = queryFactory
				.select(chatRoom, unreadCount)
				.from(chatRoom)
				.join(chatRoomMember).on(
						chatRoomMember.chatRoom.eq(chatRoom), chatRoomMember.user.id.eq(viewerId))
				.join(chatRoom.item).fetchJoin()
				.join(chatRoom.buyer).fetchJoin()
				.join(chatRoom.seller).fetchJoin()
				.leftJoin(chatRoom.listing).fetchJoin()
				.where(
						chatRoomMember.leftAt.isNull(),
						inRole(tab, viewerId),
						matchesKeyword(keyword, viewerId),
						afterLastMessageAt(cursor),
						unread(tab, unreadCount))
				.orderBy(chatRoom.lastMessageAt.desc(), chatRoom.id.desc())
				.limit(size + 1L)
				.fetch();

		return rows.stream()
				.map(row -> new ChatRoomFeedRow(row.get(chatRoom), row.get(unreadCount)))
				.toList();
	}

	private BooleanExpression inRole(ChatRoomTab tab, Long viewerId) {
		return switch (tab) {
			case BUYING -> chatRoom.buyer.id.eq(viewerId);
			case SELLING -> chatRoom.seller.id.eq(viewerId);
			case ALL, UNREAD -> chatRoom.buyer.id.eq(viewerId).or(chatRoom.seller.id.eq(viewerId));
		};
	}

	/** UNREAD 탭만 안읽음 수로 한 번 더 거른다. 나머지 탭은 전부 보여주고 수만 함께 내린다 */
	private BooleanExpression unread(ChatRoomTab tab, NumberExpression<Long> unreadCount) {
		return tab == ChatRoomTab.UNREAD ? unreadCount.gt(0L) : null;
	}

	/** 상대 닉네임 또는 아이템명. 상대는 역할에 따라 갈린다 — 내가 구매자면 판매자가 상대다 */
	private BooleanExpression matchesKeyword(String keyword, Long viewerId) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}

		return chatRoom.buyer.id.eq(viewerId).and(chatRoom.seller.nickname.containsIgnoreCase(keyword))
				.or(chatRoom.seller.id.eq(viewerId).and(chatRoom.buyer.nickname.containsIgnoreCase(keyword)))
				.or(chatRoom.item.name.containsIgnoreCase(keyword));
	}

	/**
	 * 정렬이 (lastMessageAt desc, id desc) 라 조건도 같은 순서로 비교한다.
	 *
	 * lastMessageAt 은 방 개설 시각으로 항상 채워져 있다(ChatRoom 팩토리) — null 이 아니다
	 */
	private BooleanExpression afterLastMessageAt(Cursor cursor) {
		if (cursor == null) {
			return null;
		}

		LocalDateTime lastMessageAt = cursor.asDateTime();
		return chatRoom.lastMessageAt.lt(lastMessageAt)
				.or(chatRoom.lastMessageAt.eq(lastMessageAt).and(chatRoom.id.lt(cursor.getId())));
	}
}
