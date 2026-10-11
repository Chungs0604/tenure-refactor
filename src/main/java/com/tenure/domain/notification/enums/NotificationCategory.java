package com.tenure.domain.notification.enums;

/**
 * 알림 화면 탭: '확인 필요' '아이템 소식' '거래 현황' '관심 등록' '활동'(+'전체'는 필터 없음).
 *
 * ACTIVITY 는 follow 구현 시점(2026-10-11)에 추가됐다 — 팔로우 알림이 기존 4종 어디에도 맞지 않았다.
 */
public enum NotificationCategory {

	ACTION_REQUIRED,
	ITEM_NEWS,
	TRADE_STATUS,
	WISHLIST,
	ACTIVITY;

}
