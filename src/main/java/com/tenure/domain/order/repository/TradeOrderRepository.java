package com.tenure.domain.order.repository;

import com.tenure.domain.order.entity.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {
}
