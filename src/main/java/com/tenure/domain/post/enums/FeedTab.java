package com.tenure.domain.post.enums;

/**
 * 홈 피드의 탭. 화면(SCR-014)의 '모두' · '팔로우' 다.
 *
 * DB 컬럼이 아니라 조회 조건
 */
public enum FeedTab {

	/** 모두 — 차단 관계만 제외하고 전부 */
	ALL,

	/** 팔로우 — 내가 팔로우한 사용자의 게시물만 */
	FOLLOWING
}
