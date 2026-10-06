package com.tenure.domain.chat.repository;

import com.tenure.domain.chat.entity.ChatMessage;
import com.tenure.domain.chat.entity.ChatRoomMember;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

	/** 멤버십 확인 — CHAT_403 판정의 근거. 나간 뒤에도 행은 남으므로 leftAt 은 Service 가 따로 봄 */
	@Query("select m from ChatRoomMember m where m.chatRoom.id = :chatRoomId and m.user.id = :userId")
	Optional<ChatRoomMember> findByChatRoomIdAndUserId(
			@Param("chatRoomId") Long chatRoomId, @Param("userId") Long userId);

	/**
	 * 지금 저장된 값보다 더 뒤 메시지일 때만 갱신함 — DB 레벨 조건 비교라 원자적임. 명시적 읽음
	 * 처리(read)·구독 시 백로그 읽음(markBacklogRead)·실시간 수신 중 자동 읽음(sendMessage)이
	 * 같은 멤버 행을 거의 동시에 건드릴 수 있어, 엔티티를 불러와 비교하고 되저장하는 방식으론
	 * 두 트랜잭션 중 나중에 커밋한 쪽이 앞선 갱신을 덮어쓸 수 있음(lost update) — 그래서 이
	 * 세 곳은 ChatRoomMember.markRead() 대신 이 메서드를 씀
	 */
	@Modifying
	@Query("update ChatRoomMember m set m.lastReadMessage = :message "
			+ "where m.id = :memberId and (m.lastReadMessage is null or m.lastReadMessage.id < :messageId)")
	int markReadIfNewer(
			@Param("memberId") Long memberId, @Param("message") ChatMessage message, @Param("messageId") Long messageId);
}
