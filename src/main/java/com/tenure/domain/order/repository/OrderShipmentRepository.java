package com.tenure.domain.order.repository;

import com.tenure.domain.order.entity.OrderShipment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderShipmentRepository extends JpaRepository<OrderShipment, Long> {
}
