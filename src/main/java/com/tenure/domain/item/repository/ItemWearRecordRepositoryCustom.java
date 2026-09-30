package com.tenure.domain.item.repository;

import com.tenure.global.pagination.Cursor;
import java.util.List;

/**
 * 아이템 히스토리 그리드. 커서 키는 착용일이다.
 *
 * size + 1 건을 돌려준다 — 다음 페이지 유무를 그 한 건으로 판정한다.
 */
public interface ItemWearRecordRepositoryCustom {

	/**
	 * 이 아이템이 등장한 OOTD 사진. 무효화된 인증과 삭제된 게시물은 빠진다.
	 *
	 * 돌려주는 배열은 {postId, 대표 사진 키, 촬영 비율, 착용일} 이다 — 엔티티를 통째로 읽으면
	 * 쓰지 않는 게시물 본문까지 따라온다.
	 */
	List<Object[]> findHistory(Long itemId, Cursor cursor, int size);
}
