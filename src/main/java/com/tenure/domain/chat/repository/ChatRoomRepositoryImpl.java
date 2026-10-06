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
		// 방마다 "이 조회자 기준 안읽음 수"를 서브쿼리로 계산함 — 내가 보낸 메시지는 제외함
		// (sender 제외를 빼면 보내고 나가면 내 배지가 올라감)
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
				// 이 조회자의 멤버십에 조인함 — leftAt·lastReadMessage 는 조회자 기준이라 꼭 필요함
				.join(chatRoomMember).on(
						chatRoomMember.chatRoom.eq(chatRoom), chatRoomMember.user.id.eq(viewerId))
				// 목록 행 조립에 바로 쓰는 값들이라 지연 로딩 추가 쿼리 없이 한 번에 읽음
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
				// size + 1 건을 읽어서 다음 페이지 유무를 판정함(Service 의 CursorSlice 가 자름)
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

	/** UNREAD 탭만 안읽음 수로 한 번 더 거름. 나머지 탭은 전부 보여주고 수만 함께 내림 */
	private BooleanExpression unread(ChatRoomTab tab, NumberExpression<Long> unreadCount) {
		return tab == ChatRoomTab.UNREAD ? unreadCount.gt(0L) : null;
	}

	/** 상대 닉네임 또는 아이템명. 상대는 역할에 따라 갈림 — 내가 구매자면 판매자가 상대임 */
	private BooleanExpression matchesKeyword(String keyword, Long viewerId) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}

		return chatRoom.buyer.id.eq(viewerId).and(chatRoom.seller.nickname.containsIgnoreCase(keyword))
				.or(chatRoom.seller.id.eq(viewerId).and(chatRoom.buyer.nickname.containsIgnoreCase(keyword)))
				.or(chatRoom.item.name.containsIgnoreCase(keyword));
	}

	/**
	 * 정렬이 (lastMessageAt desc, id desc) 라 조건도 같은 순서로 비교함.
	 *
	 * lastMessageAt 은 방 개설 시각으로 항상 채워져 있음(ChatRoom 팩토리) — null 이 아님.
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
