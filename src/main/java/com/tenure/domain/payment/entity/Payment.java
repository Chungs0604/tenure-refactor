package com.tenure.domain.payment.entity;

import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.payment.enums.PaymentMethod;
import com.tenure.domain.payment.enums.PaymentStatus;
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
 * 결제. 승인과 확정이 분리되며 주문보다 먼저 생성된다 — 구매자가 제안을 보낼 때 승인이 잡히고, 판매자가 수락해야 TradeOrder 가 생긴다.
 *
 * <p>이 승인/확정 분리는 우리 도메인 모델일 뿐, 토스페이먼츠 간편결제(토스페이·카카오페이·
 * 네이버페이) 자체는 confirm 호출 즉시 최종 결제가 끝난다 — 카드처럼 "임시 승인만" 해두는
 * 단계가 없다. 그래서 거절·취소·만료로 이 결제를 되돌릴 때는 상태만 바꾸는 게 아니라
 * {@code TossPaymentClient.cancel()} 로 실제 환불을 호출해야 한다({@code PaymentService} 참고).
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Payment extends BaseEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 결제는 제안 시점에 발생한다 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "offer_id", nullable = false)
	private PurchaseOffer offer;

	/** 수락되어 주문이 생긴 뒤 연결 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private TradeOrder order;

	/** '결제 수단' 3종 */
	@Enumerated(EnumType.STRING)
	@Column(name = "method", nullable = false, length = 20)
	private PaymentMethod method;

	/** 제안서·거래 상세의 '결제 수단'이 '토스뱅크 1234-1234-1234' 로 표시된다.
	 * enum 만으로는 만들 수 없어 PG 가 준 표시 문자열을 스냅샷한다 */
	@Column(name = "method_detail", length = 100)
	private String methodDetail;

	/** 승인/확정/해제 3단계 */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private PaymentStatus status;

	/** '최종 결제 금액' / '결제 금액' */
	@Column(name = "amount", nullable = false)
	private Integer amount;

	/** PG 연동 식별자. 화면 미표시 */
	/** 토스페이먼츠 paymentKey. 스펙상 최대 200자 */
	@Column(name = "pg_transaction_id", length = 200)
	private String pgTransactionId;

	/** '결제 승인이 임시로 확보되었습니다' */
	@Column(name = "authorized_at")
	private LocalDateTime authorizedAt;

	/** '판매자가 수락하면 최종 결제되며' */
	@Column(name = "captured_at")
	private LocalDateTime capturedAt;

	/** '거절·취소·만료 시 승인이 해제됩니다' */
	@Column(name = "released_at")
	private LocalDateTime releasedAt;

	/** 거래 취소에 따른 환불 시각. PaymentStatus.REFUNDED 의 발생 근거 자리(→ trade_order 제약 — 거래 취소) */
	@Column(name = "refunded_at")
	private LocalDateTime refundedAt;

	/**
	 * 결제 승인. 토스페이먼츠 confirm 호출이 이미 성공한 뒤에만 불린다 — pgTransactionId 는 그
	 * 응답의 paymentKey 를 그대로 스냅샷한다
	 */
	public static Payment authorize(
			PurchaseOffer offer, PaymentMethod method, String methodDetail,
			Integer amount, String pgTransactionId, LocalDateTime now) {
		return Payment.builder()
				.offer(offer)
				.method(method)
				.methodDetail(methodDetail)
				.status(PaymentStatus.AUTHORIZED)
				.amount(amount)
				.pgTransactionId(pgTransactionId)
				.authorizedAt(now)
				.build();
	}

	/** 판매자 수락 시 최종 결제로 확정함 */
	public void capture(LocalDateTime now) {
		this.status = PaymentStatus.CAPTURED;
		this.capturedAt = now;
	}

	/** 거절·취소·만료 시 임시 승인을 해제함 */
	public void release(LocalDateTime now) {
		this.status = PaymentStatus.RELEASED;
		this.releasedAt = now;
	}

	/** 상품 발송 전 거래 취소 시 최종 결제를 환불함 */
	public void refund(LocalDateTime now) {
		this.status = PaymentStatus.REFUNDED;
		this.refundedAt = now;
	}

	/** 주문이 생긴 뒤 연결함 */
	public void linkOrder(TradeOrder order) {
		this.order = order;
	}

}
