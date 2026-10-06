package com.tenure.domain.chat.repository;

import com.tenure.domain.chat.entity.ChatRoomMember;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

	/** 멤버십 확인 — CHAT_403 판정의 근거. 나간 뒤에도 행은 남으므로 leftAt 은 Service 가 따로 봄 */
	@Query("select m from ChatRoomMember m where m.chatRoom.id = :chatRoomId and m.user.id = :userId")
	Optional<ChatRoomMember> findByChatRoomIdAndUserId(
			@Param("chatRoomId") Long chatRoomId, @Param("userId") Long userId);
}
