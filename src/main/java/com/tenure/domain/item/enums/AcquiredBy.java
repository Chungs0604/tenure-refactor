package com.tenure.domain.item.enums;

/**
 * 상품 상세 'Tenure 기록 기준'에서 최초 소유 구간에만 '최초 등록' 뱃지가 붙고, 이후 구간은 거래로 승계된다
 */
public enum AcquiredBy {

	INITIAL_REGISTRATION,
	TRADE;

}
