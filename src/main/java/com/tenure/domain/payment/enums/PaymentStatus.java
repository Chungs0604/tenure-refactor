package com.tenure.domain.payment.enums;

/**
 * '결제 승인이 임시로 확보되었습니다. 판매자가 수락하면 최종 결제되며, 거절·취소·만료 시 승인이 해제됩니다.' + 상품 발송 전 거래 취소 시 환불(REFUNDED)
 */
public enum PaymentStatus {

	AUTHORIZED,
	CAPTURED,
	RELEASED,
	REFUNDED;

}
