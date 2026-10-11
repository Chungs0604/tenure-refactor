package com.tenure.domain.wishlist.enums;

/** 위시리스트 화면 상단 탭 */
public enum WishlistTab {

	/** 전체 */
	ALL,
	/** 판매중 — item.sale_status = ON_SALE */
	ON_SALE,
	/** 거래중 — 진행 중인 거래(trade_order)가 있는 아이템 */
	IN_TRADE;

}
