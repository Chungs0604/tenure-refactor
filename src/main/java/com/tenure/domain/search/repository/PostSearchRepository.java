package com.tenure.domain.search.repository;

import com.tenure.domain.search.dto.PostSearchCriteria;
import com.tenure.global.pagination.Cursor;
import java.util.List;

/** API-200. 정렬 5종 × 필터 조합이라 한 메서드로 받고 내부에서 분기한다. */
public interface PostSearchRepository {

	List<PostSearchRow> search(PostSearchCriteria criteria, Long viewerId, Cursor cursor, int size);

	/** 화면 상단 총 게시물 수. 검색 결과에만 있는 집계라 별도 쿼리다 (DEC-022). */
	long countMatches(PostSearchCriteria criteria, Long viewerId);
}
