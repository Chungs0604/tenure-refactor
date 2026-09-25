package com.tenure.domain.order.enums;

/**
 * 거래 상세 '거래 진행 단계' 설명 문구에서 순서 확정 + 타임라인 체크 5단계(25.05.26 결제완료 → 25.05.28 상품발송 → 25.06.01 배송완료 → 25.06.02 구매…
 */
public enum OrderStatus {

	/** 결제 완료 */
	PAYMENT_COMPLETED,
	/** 상품 발송 */
	SHIPPED,
	/** 배송 완료 */
	DELIVERED,
	/** 구매 확정 */
	PURCHASE_CONFIRMED,
	/** 정산 완료 */
	SETTLED,
	/** 거래 완료 */
	COMPLETED,
	/** 거래 취소 */
	CANCELED;

}
