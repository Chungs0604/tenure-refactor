package com.tenure.domain.order.repository;

import com.tenure.domain.order.entity.OrderStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

	/** 거래 상세의 타임라인. 단계 도달 순서대로 */
	List<OrderStatusHistory> findByOrderIdOrderByChangedAtAsc(Long orderId);
}
