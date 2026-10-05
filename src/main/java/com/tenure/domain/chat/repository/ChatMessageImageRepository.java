package com.tenure.domain.chat.repository;

import com.tenure.domain.chat.entity.ChatMessageImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageImageRepository extends JpaRepository<ChatMessageImage, Long> {

	/** 메시지 목록 조회 시 IMAGE 메시지들의 사진을 한 번에 읽는다 — N+1 방지 */
	@Query("select i from ChatMessageImage i "
			+ "where i.chatMessage.id in :chatMessageIds "
			+ "order by i.chatMessage.id, i.sortOrder")
	List<ChatMessageImage> findAllByChatMessageIdIn(@Param("chatMessageIds") Collection<Long> chatMessageIds);
}
