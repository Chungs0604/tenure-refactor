package com.tenure.domain.search.enums;

/**
 * 검색 필터 '판매 상태' 3모드 (`schema.yaml` 846행 — 전체 | 판매중 포함 | 판매중만).
 *
 * {@code item.sale_status} 자체(NOT_FOR_SALE·ON_SALE·SOLD)와는 다른 축이다 — 태그된 아이템 중
 * 하나라도 조건을 만족하면 그 게시물을 포함한다.
 */
public enum SaleStatusFilter {

	/** 필터 없음 */
	ALL,
	/** 판매 완료만 제외 — NOT_FOR_SALE 또는 ON_SALE */
	INCLUDE_ON_SALE,
	/** 판매 중인 아이템이 태그된 것만 */
	ONLY_ON_SALE;

}
