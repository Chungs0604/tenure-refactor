package com.tenure.domain.order.enums;

/**
 * 거래 상세를 보는 사람이 구매자인지 판매자인지. DB 컬럼이 아니라 조회 시점에 계산하는 값이다.
 *
 * settlement(정산 계좌) 같은 일부 필드는 SELLER 일 때만 채워진다.
 */
public enum OrderViewerRole {

	BUYER,
	SELLER;

}
