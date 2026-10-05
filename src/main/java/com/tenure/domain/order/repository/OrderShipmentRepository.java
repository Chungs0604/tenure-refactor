package com.tenure.domain.order.repository;

import com.tenure.domain.order.entity.OrderShipment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderShipmentRepository extends JpaRepository<OrderShipment, Long> {

	/** 거래당 1건. 잠금은 TradeOrderRepository.findByIdForUpdate 가 주문 행으로 이미 직렬화함 */
	Optional<OrderShipment> findByOrderId(Long orderId);
}
