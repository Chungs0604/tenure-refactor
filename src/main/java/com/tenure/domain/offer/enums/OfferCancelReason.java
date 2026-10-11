package com.tenure.domain.offer.enums;

/**
 * 제안자 본인 취소와 시스템 자동 취소 두 경우를 구분함. 시스템 취소(OTHER_OFFER_ACCEPTED·
 * LISTING_DELISTED·WITHDRAWN·ITEM_DELETED)는 제안자 잘못이 아니라 1회 제한에서 제외됨 —
 * BUYER_CANCELED 만 영구 소진임(OfferService.requireNotAlreadyUsed 참고)
 */
public enum OfferCancelReason {

	BUYER_CANCELED,
	OTHER_OFFER_ACCEPTED,
	LISTING_DELISTED,
	/** 구매자가 탈퇴해 보낸 제안이 정리됨 */
	WITHDRAWN,
	/** 미판매 아이템이 삭제되어 보낸 제안이 정리됨 */
	ITEM_DELETED;

}
