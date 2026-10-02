package com.tenure.domain.item.enums;

/**
 * 아이템의 판매 상태. item.sale_status 의 타입이다 (DEC-026).
 *
 * 상품 상세 프레임 6종이 판매중/미판매/판매완료 셋을 한 화면의 상태 변형으로 그린다.
 * 거래 진행 중을 나타내는 값은 없다 — 위시리스트의 '거래중' 탭은 trade_order 조인으로 파생시킨다.
 */
public enum SaleStatus {

	/** 미판매 */
	NOT_FOR_SALE,
	/** 판매 중 */
	ON_SALE,
	/** 판매 완료 */
	SOLD;

}
