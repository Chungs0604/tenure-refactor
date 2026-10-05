package com.tenure.domain.order.entity;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.order.enums.OrderStatus;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 거래. 제안이 수락되는 즉시 생성되어 결제 완료 단계부터 시작한다. 금액은 제안 시점 값을 스냅샷한다. 테이블명은 order 가 MySQL 예약어라 trade_order 를 쓴다.
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "trade_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class TradeOrder extends BaseEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** '주문 번호' 2026042919502395 */
	@Column(name = "order_no", nullable = false, unique = true, length = 30)
	private String orderNo;

	/** '제안이 수락되는 즉시 상품이 구매 처리되며' */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "offer_id", nullable = false)
	private PurchaseOffer offer;

	/**
	 * 거래가 성립한 판매 조건 스냅샷.
	 *
	 * 미판매 아이템의 구매 제안에서 시작한 거래는 NULL 이다 (DEC-026) — 그런 제안은
	 * purchase_offer.listing_id 도 NULL 이라 가져올 판매 등록이 없다. 판매 상태는
	 * item.sale_status 가 들고 있으므로 이 값이 없어도 '판매 완료' 화면이 성립한다.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "listing_id")
	private ItemListing listing;

	/** 거래 상세 상단 상품 정보 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false)
	private Item item;

	/** '구매자 정보' */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "buyer_id", nullable = false)
	private User buyer;

	/** '판매자 정보' */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "seller_id", nullable = false)
	private User seller;

	/** '상품 금액' 360,000원 */
	@Column(name = "item_price", nullable = false)
	private Integer itemPrice;

	/** '배송비' +5,000원 */
	@Column(name = "shipping_fee", nullable = false)
	private Integer shippingFee;

	/** '수수료' */
	@Column(name = "fee_amount", nullable = false)
	private Integer feeAmount;

	/** 제안 시점 요율 스냅샷 */
	@Column(name = "fee_rate", nullable = false, precision = 5, scale = 4)
	private BigDecimal feeRate;

	/** 구매자 부담 수수료 스냅샷 */
	@Column(name = "buyer_fee_amount", nullable = false)
	private Integer buyerFeeAmount;

	/** 판매자 부담 수수료 스냅샷 */
	@Column(name = "seller_fee_amount", nullable = false)
	private Integer sellerFeeAmount;

	/** 판매 등록의 '수수료 부담'을 스냅샷 */
	@Enumerated(EnumType.STRING)
	@Column(name = "fee_bearer", nullable = false, length = 10)
	private FeeBearer feeBearer;

	/** '총 결제 금액' 377,000원 */
	@Column(name = "total_payment_amount", nullable = false)
	private Integer totalPaymentAmount;

	/** '총 정산 금액' 365,000원 */
	@Column(name = "payout_amount", nullable = false)
	private Integer payoutAmount;

	/** 거래 단계 6종 + 취소 */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private OrderStatus status;

	/** '자동 구매 확정까지 23시간 44분 남음' = 배송 완료 +72h */
	@Column(name = "auto_confirm_at")
	private LocalDateTime autoConfirmAt;

	/** '구매를 확정할까요?' */
	@Column(name = "confirmed_at")
	private LocalDateTime confirmedAt;

	/** 거래 상세의 '거래 취소' 버튼 (→ trade_order 제약 — 거래 취소) */
	@Column(name = "canceled_at")
	private LocalDateTime canceledAt;

	public boolean isBuyer(Long userId) {
		return buyer.getId().equals(userId);
	}

	public boolean isSeller(Long userId) {
		return seller.getId().equals(userId);
	}

	/** 운송장 입력 → 상품 발송. 배송 완료 전까지 재입력 가능하므로 이미 SHIPPED 여도 다시 불러도 됨 */
	public void ship() {
		this.status = OrderStatus.SHIPPED;
	}

	/** 배송 완료 처리. 72시간 뒤 자동 구매 확정을 건다 */
	public void deliver(LocalDateTime autoConfirmAt) {
		this.status = OrderStatus.DELIVERED;
		this.autoConfirmAt = autoConfirmAt;
	}

	/** 구매 확정. 본인 확정이든 자동 확정이든 같다 */
	public void confirmPurchase(LocalDateTime confirmedAt) {
		this.status = OrderStatus.PURCHASE_CONFIRMED;
		this.confirmedAt = confirmedAt;
	}

	/** 정산 배치가 입금을 마치면 호출함. 최종 상태 */
	public void settle() {
		this.status = OrderStatus.SETTLED;
	}

	/** 상품 발송 전 거래 취소 */
	public void cancel(LocalDateTime canceledAt) {
		this.status = OrderStatus.CANCELED;
		this.canceledAt = canceledAt;
	}

	/** 제안 수락 시 생성. 금액·조건은 전부 제안 시점 스냅샷을 그대로 옮김 */
	public static TradeOrder createFromOffer(PurchaseOffer offer, String orderNo, LocalDateTime now) {
		return TradeOrder.builder()
				.orderNo(orderNo)
				.offer(offer)
				.listing(offer.getListing())
				.item(offer.getItem())
				.buyer(offer.getBuyer())
				.seller(offer.getSeller())
				.itemPrice(offer.getOfferPrice())
				.shippingFee(offer.getShippingFee())
				.feeAmount(offer.getFeeAmount())
				.feeRate(offer.getFeeRate())
				.buyerFeeAmount(offer.getBuyerFeeAmount())
				.sellerFeeAmount(offer.getSellerFeeAmount())
				.feeBearer(offer.getFeeBearer())
				.totalPaymentAmount(offer.getTotalPaymentAmount())
				.payoutAmount(offer.getExpectedPayoutAmount())
				.status(OrderStatus.PAYMENT_COMPLETED)
				.build();
	}

}
