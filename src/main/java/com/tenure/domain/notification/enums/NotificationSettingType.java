package com.tenure.domain.notification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 알림 설정 화면(3063:28457)의 토글 9종.
 *
 * <p>알림 목록 화면의 탭({@link NotificationCategory})과 <b>축이 다르다</b> —
 * 설정은 '무엇을 받을지', 탭은 '무엇을 모아볼지'다. v3 는 한 enum 을 공유해 두 축이 섞여 있었다.
 *
 * <p>가입 시 9종을 모두 만든다. 기본값은 항목마다 다르다.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationSettingType {

	TRADE_REQUEST(Group.REQUIRED, "거래 요청", "새로운 거래 요청이 오면 알려드려요.", true),
	REQUEST_STATUS(Group.REQUIRED, "요청 상태", "보낸 거래 요청의 진행 상태를 전해드려요.", true),
	REQUEST_AUTO_CANCEL(Group.REQUIRED, "요청 자동 취소", "대기 중인 요청이 자동으로 취소되면 알려드려요.", true),
	TRADE_STATUS(Group.REQUIRED, "거래 상태", "결제부터 정산까지 거래 진행 상황을 전해드려요.", true),

	CHAT(Group.ACTIVITY, "채팅 알림", "상대방에게 새 채팅이 오면 알림을 받아요.", true),
	WISHLIST_ITEM(Group.ACTIVITY, "관심 아이템", "관심 등록한 아이템의 가격 변동이나 거래 상태를 알려드려요.", true),
	FOLLOW(Group.ACTIVITY, "팔로우 알림", "누군가 나를 팔로우하면 알림을 받아요.", false),

	SERVICE_NEWS(Group.SERVICE, "서비스 소식", "공지사항과 서비스 운영 관련 소식을 받아요.", false),
	MARKETING(Group.SERVICE, "혜택 및 마케팅", "이벤트와 혜택 등 광고성 정보를 받아요.", false);

	/** 화면의 그룹 헤더 */
	private final Group group;
	/** 토글 제목 */
	private final String title;
	/** 토글 설명 */
	private final String description;
	/** 가입 시 기본값 */
	private final boolean defaultEnabled;

	public enum Group {
		REQUIRED("필수"),
		ACTIVITY("활동 알림"),
		SERVICE("서비스 알림");

		private final String label;

		Group(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}
}
