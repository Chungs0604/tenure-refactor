package com.tenure.domain.chat.repository;

import com.tenure.domain.chat.enums.ChatRoomTab;
import com.tenure.global.pagination.Cursor;
import java.util.List;

public interface ChatRoomRepositoryCustom {

	/**
	 * 채팅방 목록. tab·keyword 로 걸러 커서 페이징함.
	 *
	 * 나간 방(이 조회자의 ChatRoomMember.leftAt 이 있음)은 제외함 — 나가기는 내 목록에서
	 * 지우는 동작이고 상대 목록에는 그대로 남음.
	 */
	List<ChatRoomFeedRow> findAllForFeed(
			Long viewerId, ChatRoomTab tab, String keyword, Cursor cursor, int size);
}
