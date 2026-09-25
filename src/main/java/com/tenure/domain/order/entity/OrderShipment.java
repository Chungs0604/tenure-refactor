package com.tenure.domain.order.entity;

import com.tenure.domain.order.enums.CourierCode;
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
 * 배송 정보. 배송지는 ShippingAddress 를 FK 로 걸지 않고 값을 스냅샷한다.
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "order_shipment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class OrderShipment {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 거래당 1건 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private TradeOrder order;

	/** '수령인' / '받는 분' 홍길동 */
	@Column(name = "recipient_name", nullable = false, length = 50)
	private String recipientName;

	/** '연락처' 010-1234-5678 */
	@Column(name = "phone", nullable = false, length = 20)
	private String phone;

	/** '주소' 서울특별시 동작구 상도로 369 */
	@Column(name = "address", nullable = false, length = 255)
	private String address;

	/** '상세주소' 112동 201호 */
	@Column(name = "address_detail", length = 255)
	private String addressDetail;

	/** '요청사항' 배송 전에 미리 연락 바랍니다. */
	@Column(name = "delivery_request", length = 255)
	private String deliveryRequest;

	/** '택배사' CJ 택배 */
	@Enumerated(EnumType.STRING)
	@Column(name = "courier_code", length = 30)
	private CourierCode courierCode;

	/** '운송장 번호' 1234123412349 */
	@Column(name = "tracking_no", length = 50)
	private String trackingNo;

	/** '상품 발송' 25.05.28 */
	@Column(name = "shipped_at")
	private LocalDateTime shippedAt;

	/** '배송을 완료 처리할까요?'. 이 시각 +72h 가 auto_confirm_at */
	@Column(name = "delivered_at")
	private LocalDateTime deliveredAt;

}
