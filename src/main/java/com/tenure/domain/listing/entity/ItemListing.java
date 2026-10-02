package com.tenure.domain.listing.entity;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.user.entity.User;
import com.tenure.global.entity.BaseEntity;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아이템을 어떤 조건으로 팔았는지를 담는 판매 조건 스냅샷('판매 전환'). 판매 전환을 누를 때마다
 * 새 행이 생기므로 Item 과 1:N 이고, 열린 행(closed_at IS NULL)은 아이템당 최대 1개다.
 * 생성 전제로 판매자에게 PayoutAccount 가 있어야 한다.
 *
 * 판매 상태는 여기 없다 — 판매 등록이 없는 아이템도 '미판매'·'판매 완료' 를 표시해야 해서
 * item.sale_status 로 올렸다 (DEC-026). 관심 수와 구매 제안 허용도 같은 이유로 Item 에 있다 (DEC-024).
 *
 * 생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. @Builder 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "item_listing")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class ItemListing extends BaseEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 판매 대상 아이템 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false)
	private Item item;

	/** '판매자 정보' 섹션 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "seller_id", nullable = false)
	private User seller;

	/** '가격' 320,000원 (필수 표시 *). 미판매는 행이 없거나 닫힌 행이지 가격이 빈 행이 아니다 */
	@Column(name = "price", nullable = false)
	private Integer price;

	/** '배송비' +5,000원. 기본 사용자는 고정값 */
	@Column(name = "shipping_fee", nullable = false)
	private Integer shippingFee;

	/** '수수료 부담'. 기본 사용자는 고정값 */
	@Enumerated(EnumType.STRING)
	@Column(name = "fee_bearer", nullable = false, length = 10)
	private FeeBearer feeBearer;

	/** '판매자 설명' */
	@Column(name = "seller_note", columnDefinition = "TEXT")
	private String sellerNote;

	/** '판매 게시가 완료됐습니다.' — 행이 생기는 순간이 곧 게시 시각 */
	@Column(name = "listed_at", nullable = false)
	private LocalDateTime listedAt;

	/** '미판매로 변경시 화면' — 판매 내림 시각. NULL 이면 열린 판매 등록이다 */
	@Column(name = "closed_at")
	private LocalDateTime closedAt;

	/**
	 * 판매 전환. 아이템당 열린 행이 하나뿐인지는 Service 가 확인한다 —
	 * 다른 행을 읽어야 알 수 있어 엔티티가 판정할 수 없다.
	 *
	 * 기본 사용자는 수수료 부담과 배송비를 고를 수 없다(화면에서 잠겨 있다). 그 판정은
	 * 판매자의 등급을 봐야 하므로 Service 가 하고, 여기서는 받은 값을 반영만 한다.
	 */
	public static ItemListing open(
			Item item, User seller, Integer price, Integer shippingFee,
			FeeBearer feeBearer, String sellerNote, LocalDateTime listedAt
	) {
		return ItemListing.builder()
				.item(item)
				.seller(seller)
				.price(price)
				.shippingFee(shippingFee)
				.feeBearer(feeBearer)
				.sellerNote(sellerNote)
				.listedAt(listedAt)
				.build();
	}

	/**
	 * 판매 조건 수정. 수정 화면이 판매 전환과 같은 전체 폼이라 넷을 통째로 갈아끼운다.
	 *
	 * 이미 전송된 제안의 금액은 purchase_offer 가 자체 스냅샷으로 들고 있어 바뀌지 않는다.
	 */
	public void changeTerms(
			Integer price, Integer shippingFee, FeeBearer feeBearer, String sellerNote
	) {
		this.price = price;
		this.shippingFee = shippingFee;
		this.feeBearer = feeBearer;
		this.sellerNote = sellerNote;
	}

	/**
	 * 판매를 내린다. 닫힌 행은 다시 열지 않는다 — 다시 팔려면 새 행을 만든다 (DEC-026).
	 *
	 * 이 행을 가리키는 purchase_offer·chat_room 의 조건 스냅샷이 사후에 바뀌면 안 되기 때문이다.
	 */
	public void close(LocalDateTime closedAt) {
		this.closedAt = closedAt;
	}

	public boolean isClosed() {
		return closedAt != null;
	}

	/**
	 * 지금 걸려 있는 판매 등록인지.
	 *
	 * 열린 행은 아이템당 최대 하나이고 늘 최신 행이다 — 판매 전환이 열린 행이 있으면 거부하므로,
	 * 더 늦게 만들어진 행이 있다는 것은 그 전 행이 닫혔다는 뜻이다. 조회는 이 성질을 써서
	 * 최신 행 한 번만 읽고 열림 여부를 가린다.
	 */
	public boolean isOpen() {
		return closedAt == null;
	}

	public boolean isSeller(Long userId) {
		return seller.getId().equals(userId);
	}

}
