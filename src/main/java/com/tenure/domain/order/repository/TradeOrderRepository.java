package com.tenure.domain.order.repository;

import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.order.enums.OrderStatus;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {

	/**
	 * 아직 끝나지 않은 거래가 있는지. 아이템 삭제를 막는 판정이다.
	 *
	 * 끝난 상태(거래 완료·거래 취소)만 빼고 본다 — 새 상태가 중간에 생겨도 자동으로 '진행 중' 에
	 * 포함되도록, 진행 중 목록을 나열하는 대신 종료 상태를 제외한다.
	 */
	@Query("select count(o) > 0 from TradeOrder o " +
			"where o.item.id = :itemId " +
			"and o.status not in :finishedStatuses")
	boolean existsInProgress(
			@Param("itemId") Long itemId,
			@Param("finishedStatuses") Collection<OrderStatus> finishedStatuses
	);
}
