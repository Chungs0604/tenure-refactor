package com.tenure.domain.offer.enums;

/**
 * 제안자 본인 취소와, 같은 아이템의 다른 요청이 수락돼 시스템이 자동 취소한 경우를 구분한다
 */
public enum OfferCancelReason {

	BUYER_CANCELED,
	OTHER_OFFER_ACCEPTED;

}
