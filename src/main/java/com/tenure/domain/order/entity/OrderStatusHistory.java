package com.tenure.domain.order.entity;

import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.user.entity.User;
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
 * 거래 진행 단계 이력. 거래 상세의 타임라인이 이 테이블이다.
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "order_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class OrderStatusHistory {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 부모 거래 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private TradeOrder order;

	/** OrderStatus 각 값의 발생 근거 자리 */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private OrderStatus status;

	/** NULL 이면 시스템(자동 확정·정산) */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "changed_by")
	private User changedBy;

	/** 타임라인 각 단계의 날짜 */
	@Column(name = "changed_at", nullable = false)
	private LocalDateTime changedAt;

	/** 타임라인 한 줄 기록. changedBy 가 null 이면 시스템(자동 확정·정산)이 올린 단계임 */
	public static OrderStatusHistory of(TradeOrder order, OrderStatus status, User changedBy, LocalDateTime changedAt) {
		return OrderStatusHistory.builder()
				.order(order)
				.status(status)
				.changedBy(changedBy)
				.changedAt(changedAt)
				.build();
	}

}
