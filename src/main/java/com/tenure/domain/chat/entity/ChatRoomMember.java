package com.tenure.domain.chat.entity;

import com.tenure.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 참여자별 읽음 위치와 나가기 상태. 안읽음 배지와 '읽지않음' 필터의 원천.
 *
 * 생성 메서드는 기능 구현 시 추가함 — 무엇이 필수 인자인지는 도메인 규칙이라 스키마에서
 * 읽을 수 없음. 빌더가 PRIVATE 이라 팩토리 없이는 만들 수 없음.
 */
@Entity
@Table(name = "chat_room_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class ChatRoomMember {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 부모 FK */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chat_room_id", nullable = false)
	private ChatRoom chatRoom;

	/** 참여자 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** 안읽음 수 계산 기준. 배지 '1' '3' */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "last_read_message_id")
	private ChatMessage lastReadMessage;

	/** '채팅방 나가기' */
	@Column(name = "left_at")
	private LocalDateTime leftAt;

	/** 방 개설 시 buyer·seller 양쪽에 1건씩 만든다 */
	public static ChatRoomMember join(ChatRoom chatRoom, User user) {
		return ChatRoomMember.builder()
				.chatRoom(chatRoom)
				.user(user)
				.build();
	}

	/**
	 * 읽음 처리. 단조 증가만 허용함 — 요청이 뒤섞여 도착해도 읽음 위치가 뒤로 밀리지 않음.
	 * 이미 더 최신까지 읽었으면 조용히 무시함(에러로 다루지 않음 — 클라이언트 재시도·중복
	 * 전송에서 흔한 입력임).
	 *
	 * 테스트 전용임. 프로덕션 경로는 동시 갱신에 따른 lost update 를 피하려고 전부
	 * ChatRoomMemberRepository.markReadIfNewer() 를 씀 — 지우지 말고 테스트에서만 쓴다.
	 */
	public void markRead(ChatMessage lastReadMessage) {
		if (this.lastReadMessage != null && lastReadMessage.getId() <= this.lastReadMessage.getId()) {
			return;
		}
		this.lastReadMessage = lastReadMessage;
	}

	/** 채팅방 나가기. 행은 지우지 않고 이 시각만 찍는다 */
	public void leave(LocalDateTime now) {
		this.leftAt = now;
	}

	/** 나간 방에 다시 들어옴. 아이템 상세에서 '채팅하기'를 다시 누르는 경로(ChatService.open) */
	public void rejoin() {
		this.leftAt = null;
	}

	public boolean hasLeft() {
		return leftAt != null;
	}

}
