package com.tenure.domain.chat.entity;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.user.entity.User;
import com.tenure.global.entity.BaseCreatedEntity;
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
 * 아이템별 1:1 채팅방. 헤더에 아이템 정보('판매 중 · 최근 착용 1일 전')와 거래 단계별 CTA 가 붙는다.
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "chat_room")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class ChatRoom extends BaseCreatedEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 채팅방 헤더의 아이템. 'Levis / LVC 1955 501' */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false)
	private Item item;

	/** 헤더의 가격·판매 상태. 미판매 아이템이면 NULL 가능 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "listing_id")
	private ItemListing listing;

	/** '구매 채팅' 필터의 기준 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "buyer_id", nullable = false)
	private User buyer;

	/** '판매 채팅' 필터의 기준 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "seller_id", nullable = false)
	private User seller;

	/** 목록의 '6월 27일' 최근 메시지 시각 정렬 */
	@Column(name = "last_message_at")
	private LocalDateTime lastMessageAt;

}
