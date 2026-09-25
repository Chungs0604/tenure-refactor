package com.tenure.domain.offer.enums;

/**
 * '응답 대기' 칩 + '구매 제안을 수락할까요?' / '거래 의사를 거절할까요?' / '구매 제안을 취소할까요?' / '판매자가 24시간 안에 응답하지 않으면 자동으로 취소됩니다'
 */
public enum OfferStatus {

	/** 응답 대기 */
	PENDING,
	ACCEPTED,
	REJECTED,
	CANCELED,
	EXPIRED;

}
