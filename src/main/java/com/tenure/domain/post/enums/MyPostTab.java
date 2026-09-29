package com.tenure.domain.post.enums;

/**
 * 마이페이지의 탭. 화면(SCR-020)의 '게시물' · '좋아요' · '저장' 이다.
 *
 * DB 컬럼이 아니라 조회 조건이다.
 */
public enum MyPostTab {

	/** 게시물 — 내가 쓴 것 */
	POSTS,

	/** 좋아요 — 내가 좋아요한 것 */
	LIKED,

	/** 저장 — 내가 저장한 것 */
	SAVED
}
