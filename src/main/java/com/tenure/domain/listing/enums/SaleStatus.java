package com.tenure.domain.listing.enums;

/**
 * 상품 상세 프레임 4종: '판매 상품 상세페이지' / '미판매 상품 상세 + 구매제안 on' / '미판매 상품 상세 - 구매제안 off' / '판매 완료 상세 페이지'. 상태 변경 프레임…
 */
public enum SaleStatus {

	/** 미판매 */
	NOT_FOR_SALE,
	/** 판매 중 */
	ON_SALE,
	/** 판매 완료 */
	SOLD;

}
