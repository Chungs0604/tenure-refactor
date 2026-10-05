package com.tenure.domain.order.enums;

/**
 * 판매·구매 내역 목록의 행 종류. purchase_offer(PENDING)와 trade_order 를 한 목록에
 * rowType 유니온으로 섞어 내리기 위한 구분값이다(DEC-103). DB 컬럼이 아니다.
 */
public enum OrderFeedRowType {

	OFFER,
	ORDER;

}
