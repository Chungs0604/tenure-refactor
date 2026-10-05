package com.tenure.domain.order.repository;

import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.global.pagination.Cursor;
import java.util.Collection;
import java.util.List;

public interface TradeOrderRepositoryCustom {

	/**
	 * 판매 내역의 거래(ORDER) 행. statuses 가 null 이면 상태 무관(전체 탭), 아니면 그 상태들만
	 * (거래중/거래완료 탭)
	 */
	List<TradeOrder> findAllBySellerForFeed(Long sellerId, Collection<OrderStatus> statuses, Cursor cursor, int size);

	/** 구매 내역의 거래(ORDER) 행. 판매 쪽과 대칭 */
	List<TradeOrder> findAllByBuyerForFeed(Long buyerId, Collection<OrderStatus> statuses, Cursor cursor, int size);
}
