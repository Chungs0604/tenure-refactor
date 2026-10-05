package com.tenure.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 이미지 메시지의 사진. 한 말풍선에 여러 장이 묶여 나가므로 메시지와 1:N 이다(DEC-006).
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "chat_message_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class ChatMessageImage {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 부모 FK */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chat_message_id", nullable = false)
	private ChatMessage chatMessage;

	/** 저장소 키 */
	@Column(name = "image_key", nullable = false, length = 500)
	private String imageKey;

	/** 말풍선 안의 노출 순서 */
	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	/** 메시지 전송 시 장수만큼 한 번에 만든다. 순서는 요청의 imageKeys 배열 순서를 그대로 따른다 */
	public static ChatMessageImage of(ChatMessage chatMessage, String imageKey, Integer sortOrder) {
		return ChatMessageImage.builder()
				.chatMessage(chatMessage)
				.imageKey(imageKey)
				.sortOrder(sortOrder)
				.build();
	}

}
