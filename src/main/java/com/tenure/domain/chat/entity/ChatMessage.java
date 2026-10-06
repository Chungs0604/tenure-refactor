package com.tenure.domain.chat.entity;

import com.tenure.domain.chat.enums.ChatMessageType;
import com.tenure.domain.user.entity.User;
import com.tenure.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅 메시지.
 *
 * 생성 메서드는 기능 구현 시 추가함 — 무엇이 필수 인자인지는 도메인 규칙이라 스키마에서
 * 읽을 수 없음. 빌더가 PRIVATE 이라 팩토리 없이는 만들 수 없음.
 */
@Entity
@Table(name = "chat_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class ChatMessage extends BaseCreatedEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 부모 FK */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chat_room_id", nullable = false)
	private ChatRoom chatRoom;

	/** 발신자. 좌/우 말풍선 구분 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sender_id", nullable = false)
	private User sender;

	/** 텍스트 / '사진' */
	@Enumerated(EnumType.STRING)
	@Column(name = "message_type", nullable = false, length = 10)
	private ChatMessageType messageType;

	/** 메시지 본문. TEXT 일 때만 채운다 — IMAGE 는 chat_message_image 에 여러 장을 담는다 */
	@Column(name = "content", columnDefinition = "TEXT")
	private String content;

	/** 텍스트 메시지 */
	public static ChatMessage ofText(ChatRoom chatRoom, User sender, String content) {
		return ChatMessage.builder()
				.chatRoom(chatRoom)
				.sender(sender)
				.messageType(ChatMessageType.TEXT)
				.content(content)
				.build();
	}

	/** 이미지 메시지. content 는 비우고 이미지는 ChatMessageImage 로 별도 저장한다 */
	public static ChatMessage ofImage(ChatRoom chatRoom, User sender) {
		return ChatMessage.builder()
				.chatRoom(chatRoom)
				.sender(sender)
				.messageType(ChatMessageType.IMAGE)
				.build();
	}

}
