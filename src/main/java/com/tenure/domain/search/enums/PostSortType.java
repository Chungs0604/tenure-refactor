package com.tenure.domain.search.enums;

/** 게시물 검색 정렬 5종 (API-200). 기본값은 {@link #RECOMMENDED} */
public enum PostSortType {

	/** 매칭 등급 → rank_score → id (DEC-023) */
	RECOMMENDED,
	/** created_at desc */
	LATEST,
	/** like_count desc */
	LIKE,
	/** view_count desc (DEC-019) */
	VIEW,
	/** save_count desc */
	SAVE;

}
