package com.tenure.domain.offer.entity;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.offer.enums.OfferCancelReason;
import com.tenure.domain.offer.enums.OfferStatus;
import com.tenure.domain.offer.enums.OfferType;
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
 * 구매 제안 / 거래 의사. 대상 상태에 따라 두 종류로 나뉘며 생명주기가 같아 한 테이블로 둔다. 두 경우 모두 결제 승인이 먼저 잡히고 판매자 수락 시 최종 결제된다.
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "purchase_offer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class PurchaseOffer extends BaseEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 제안 대상 판매 등록 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "listing_id", nullable = false)
	private ItemListing listing;

	/** 1회 제한 판정을 아이템 기준으로 하기 위해 중복 보유(→ purchase_offer 제약 — 아이템당 1회) */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false)
	private Item item;

	/** '구매자 정보' 섹션 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "buyer_id", nullable = false)
	private User buyer;

	/** '판매자 정보' 섹션. 'YuJin님에게' */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "seller_id", nullable = false)
	private User seller;

	/** 구매 제안 / 거래 의사 */
	@Enumerated(EnumType.STRING)
	@Column(name = "offer_type", nullable = false, length = 20)
	private OfferType offerType;

	/** '제안 금액' / '제안 가격' 360,000원 */
	@Column(name = "offer_price", nullable = false)
	private Integer offerPrice;

	/** '배송비' +5,000원 */
	@Column(name = "shipping_fee", nullable = false)
	private Integer shippingFee;

	/** '수수료'. 화면 예시 0원(판매자 기준) / +12,000원(구매자 기준) (→ purchase_offer 제약 — 수수료 계산) */
	@Column(name = "fee_amount", nullable = false)
	private Integer feeAmount;

	/** 요청 시점에 확정되는 수수료 부담. 소유자가 BASIC 이면 SELLER 로 강제된다 */
	@Enumerated(EnumType.STRING)
	@Column(name = "fee_bearer", nullable = false, length = 10)
	private FeeBearer feeBearer;

	/** 적용 요율 스냅샷. 요청 전송 시점의 소유자 등급 기준(BASIC 0.06 / RECORD 0.03) */
	@Column(name = "fee_rate", nullable = false, precision = 5, scale = 4)
	private BigDecimal feeRate;

	/** 구매자 부담 수수료. SPLIT 이면 floor(총액/2) */
	@Column(name = "buyer_fee_amount", nullable = false)
	private Integer buyerFeeAmount;

	/** 판매자 부담 수수료. SPLIT 이면 총액 - 구매자분(홀수 1원은 판매자) */
	@Column(name = "seller_fee_amount", nullable = false)
	private Integer sellerFeeAmount;

	/** '총 결제 금액' 377,000원 (구매자 기준) */
	@Column(name = "total_payment_amount", nullable = false)
	private Integer totalPaymentAmount;

	/** '총 정산 예정 금액' 365,000원 (판매자 기준) */
	@Column(name = "expected_payout_amount", nullable = false)
	private Integer expectedPayoutAmount;

	/** '거래 요청사항' / '구매자 요청사항' */
	@Column(name = "buyer_request", length = 500)
	private String buyerRequest;

	/** '응답 대기' 칩 */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private OfferStatus status;

	/** '제안 만료까지 23시간 18분 남음' = 생성 +24h */
	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	/** 판매자 수락/거절 시각. ACCEPTED·REJECTED 의 근거 */
	@Column(name = "responded_at")
	private LocalDateTime respondedAt;

	/** '구매 제안을 취소할까요?' — CANCELED 의 근거 */
	@Column(name = "canceled_at")
	private LocalDateTime canceledAt;

	/** 취소 사유. 자동 취소는 1회 제한에서 제외된다 */
	@Enumerated(EnumType.STRING)
	@Column(name = "cancel_reason", length = 30)
	private OfferCancelReason cancelReason;

	/** 24h 만료 배치 기록. EXPIRED 의 근거 */
	@Column(name = "expired_at")
	private LocalDateTime expiredAt;

}
