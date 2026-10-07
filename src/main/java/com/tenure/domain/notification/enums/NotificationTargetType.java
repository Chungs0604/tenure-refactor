package com.tenure.domain.notification.enums;

/**
 * 알림 딥링크 대상. offer 가 OFFER·ORDER 로, chat 이 CHAT_ROOM 으로, listing 이 ITEM 으로
 * 발행한다(2026-10-07 전체 확정). POST 는 발행하는 곳이 없어 빼 두었다 — 필요해지면 다시 넣는다.
 */
public enum NotificationTargetType {

	ITEM,
	ORDER,
	OFFER,
	CHAT_ROOM;

}
