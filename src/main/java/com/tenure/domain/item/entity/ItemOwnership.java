package com.tenure.domain.item.entity;

import com.tenure.domain.item.enums.AcquiredBy;
import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.user.entity.User;
import com.tenure.global.entity.BaseCreatedEntity;
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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아이템의 소유 구간. 상품 상세 'Tenure 기록 기준' 섹션이 이 테이블 그대로다.
 * 거래 완료 시 이전 행의 end_date 를 닫고 구매자 행을 새로 연다.
 *
 * 생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. @Builder 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "item_ownership")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class ItemOwnership extends BaseCreatedEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 대상 아이템 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false)
	private Item item;

	/** 구간 소유자. 화면에 닉네임 표시 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;

	/** '25.02.8 ~' 구간 시작. **최초 구간은 item.first_owned_date 를 그대로 쓴다.** 거래로 승계된 구간은 구매 확정일 */
	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	/** '~ 26.07.10' 구간 종료. NULL 이면 '~ 현재' */
	@Column(name = "end_date")
	private LocalDate endDate;

	/** '최초 등록' 뱃지 유무 */
	@Enumerated(EnumType.STRING)
	@Column(name = "acquired_by", nullable = false, length = 30)
	private AcquiredBy acquiredBy;

	/** acquired_by=TRADE 일 때 근거 거래. AcquiredBy.TRADE 의 발생 근거 자리 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private TradeOrder order;


	/** 끝난 구간이면 true. 종료된 구간의 태그는 수정·삭제를 막는다. */
	public boolean isClosed() {
		return endDate != null;
	}


	/**
	 * 최초 소유 구간. 시작일은 item.first_owned_date 와 같은 값이다 (DEC-007).
	 *
	 * 거래로 승계된 구간은 근거 거래를 갖는다 — 그 팩토리는 order 도메인을 만들 때 추가한다.
	 */
	public static ItemOwnership openInitial(Item item, User owner, LocalDate startDate) {
		return ItemOwnership.builder()
				.item(item)
				.owner(owner)
				.startDate(startDate)
				.acquiredBy(AcquiredBy.INITIAL_REGISTRATION)
				.build();
	}

	/**
	 * 구간 종료. 거래 완료 시 이전 소유자의 구간을 닫는다.
	 *
	 * append-only 테이블에서 허용하는 **유일한** UPDATE 다 — start_date 는 등록 뒤에 바뀌지 않는다 (DEC-007).
	 */
	public void close(LocalDate endDate) {
		this.endDate = endDate;
	}

}
