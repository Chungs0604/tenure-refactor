package com.tenure.domain.notification.enums;

/**
 * 알림 딥링크 대상. offer 가 OFFER·ORDER 로, chat 이 CHAT_ROOM 으로, listing 이 ITEM 으로
 * 발행한다(2026-10-07 전체 확정). POST 는 발행하는 곳이 없어 빼 두었다 — 필요해지면 다시 넣는다.
 *
 * USER 는 follow 구현 시점(2026-10-11)에 추가됐다 — 팔로우 알림의 딥링크 대상은 팔로우한
 * 사람의 프로필이다. 탈퇴해도 행을 지우지 않고 익명화만 하므로(DEC-004) item 과 달리
 * '삭제됨' 판정은 두지 않는다.
 */
public enum NotificationTargetType {

	ITEM,
	ORDER,
	OFFER,
	CHAT_ROOM,
	USER;

}
