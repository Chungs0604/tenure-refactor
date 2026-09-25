package com.tenure.domain.settlement.entity;

import com.tenure.domain.order.entity.TradeOrder;
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
 * 판매자 정산. 정산 계좌도 스냅샷한다.
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "settlement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Settlement extends BaseCreatedEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 거래당 1건 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private TradeOrder order;

	/** 정산 수취인 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "seller_id", nullable = false)
	private User seller;

	/** '정산 계좌' 토스뱅크 — 스냅샷 */
	@Column(name = "bank_code", nullable = false, length = 20)
	private String bankCode;

	/** 1111-1111-1111 — 스냅샷 */
	@Column(name = "account_number", nullable = false, length = 50)
	private String accountNumber;

	/** 김유진 — 스냅샷 */
	@Column(name = "account_holder", nullable = false, length = 50)
	private String accountHolder;

	/** '정산 금액' / '총 정산 금액' 365,000원 */
	@Column(name = "amount", nullable = false)
	private Integer amount;

	/** '정산 완료' 25.06.04 (→ settlement 제약 — 정산 예정일) */
	@Column(name = "settled_at")
	private LocalDateTime settledAt;

}
