package com.tenure.domain.offer.repository;

import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.global.pagination.Cursor;
import java.util.List;

public interface PurchaseOfferRepositoryCustom {

	/**
	 * 구매 내역 전체 탭 전용. 상태 무관(거절·만료·취소도 포함)으로 이 구매자의 전체 제안 이력을
	 * 커서 페이지네이션한다 — 판매 쪽 PENDING 과 달리 시간이 지날수록 쌓이는 쪽이라 bound 되지 않는다
	 */
	List<PurchaseOffer> findAllByBuyerForFeed(Long buyerId, Cursor cursor, int size);
}
