package com.tenure.domain.chat.entity;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.offer.entity.PurchaseOffer;
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
 * 아이템별 1:1 채팅방. 헤더에 아이템 정보('판매 중 · 최근 착용 1일 전')와 거래 단계별 CTA 가 붙음.
 *
 * 생성 메서드는 기능 구현 시 추가함 — 무엇이 필수 인자인지는 도메인 규칙이라 스키마에서
 * 읽을 수 없음. 빌더가 PRIVATE 이라 팩토리 없이는 만들 수 없음.
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

	/** 미판매+제안 방의 가격 출처. listing 과 배타적임 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "purchase_offer_id")
	private PurchaseOffer purchaseOffer;

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

	/**
	 * 미판매 아이템 구매 제안으로 방을 엶. listing 은 없음 — 판매중 채팅방 개설은 별도 흐름임.
	 *
	 * lastMessageAt 을 개설 시각으로 먼저 채움 — 목록 정렬·커서 기준이 메시지와 개설을
	 * 모두 '활동'으로 보게 해서, 메시지가 아직 없는 방도 null 없이 자연스럽게 섞임.
	 */
	public static ChatRoom openForOffer(Item item, User buyer, User seller, PurchaseOffer purchaseOffer, LocalDateTime now) {
		return ChatRoom.builder()
				.item(item)
				.buyer(buyer)
				.seller(seller)
				.purchaseOffer(purchaseOffer)
				.lastMessageAt(now)
				.build();
	}

	/** 판매중 아이템의 채팅. 가격 출처는 listing — purchaseOffer 는 두지 않음(배타적) */
	public static ChatRoom openForListing(Item item, ItemListing listing, User buyer, User seller, LocalDateTime now) {
		return ChatRoom.builder()
				.item(item)
				.listing(listing)
				.buyer(buyer)
				.seller(seller)
				.lastMessageAt(now)
				.build();
	}

	/** 미판매+제안허용 아이템을 아직 제안 없이 염. 헤더 가격은 제안 전까지 null */
	public static ChatRoom openForUnsold(Item item, User buyer, User seller, LocalDateTime now) {
		return ChatRoom.builder()
				.item(item)
				.buyer(buyer)
				.seller(seller)
				.lastMessageAt(now)
				.build();
	}

	/** 재제안 시 가격 출처를 최신 제안으로 갱신함 */
	public void updateOfferPointer(PurchaseOffer purchaseOffer) {
		this.purchaseOffer = purchaseOffer;
	}

	/**
	 * 미판매로 열린 방이 나중에 판매 전환되면 가격 출처를 listing 으로 옮긴다.
	 * 옮기지 않으면 판매중 아이템인데 헤더 가격이 옛 제안가(또는 null)로 멈춘다.
	 */
	public void updateListingPointer(ItemListing listing) {
		this.listing = listing;
	}

	/** 메시지 전송과 같은 트랜잭션에서 동기 갱신 */
	public void updateLastMessageAt(LocalDateTime lastMessageAt) {
		this.lastMessageAt = lastMessageAt;
	}

}
