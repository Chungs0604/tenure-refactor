package com.tenure.domain.chat.repository;

import com.tenure.domain.chat.entity.ChatMessage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	/**
	 * 메시지 목록(API-183). 최신부터 오래된 순으로 거슬러 올라간다 — 정렬·커서 키가 모두 id.
	 * cursor 가 없으면(첫 페이지) 최신부터.
	 */
	@Query("select m from ChatMessage m "
			+ "where m.chatRoom.id = :chatRoomId "
			+ "and (:cursorId is null or m.id < :cursorId) "
			+ "order by m.id desc")
	List<ChatMessage> findAllByChatRoomId(
			@Param("chatRoomId") Long chatRoomId, @Param("cursorId") Long cursorId, Pageable pageable);

	/**
	 * 채팅방 목록(API-180)의 미리보기 한 줄. 방마다 최신 메시지 하나 — 상관 서브쿼리로
	 * 그룹별 최댓값만 남긴다(ItemWearRecordRepository.findOwnershipPreviews 와 같은 기법)
	 */
	@Query("select m from ChatMessage m "
			+ "where m.chatRoom.id in :chatRoomIds "
			+ "and m.id = (select max(m2.id) from ChatMessage m2 where m2.chatRoom.id = m.chatRoom.id)")
	List<ChatMessage> findLatestByChatRoomIdIn(@Param("chatRoomIds") Collection<Long> chatRoomIds);
}
