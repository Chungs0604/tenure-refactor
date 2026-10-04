package com.tenure.domain.offer.enums;

/**
 * 제안자 본인 취소와 시스템 자동 취소 두 경우를 구분함. 시스템 취소(OTHER_OFFER_ACCEPTED·
 * LISTING_DELISTED)는 제안자 잘못이 아니라 1회 제한에서 제외됨 — BUYER_CANCELED 만 영구 소진임
 */
public enum OfferCancelReason {

	BUYER_CANCELED,
	OTHER_OFFER_ACCEPTED,
	LISTING_DELISTED;

}
