package com.tenure.domain.item.repository;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.enums.RecommendationBlock;
import com.tenure.domain.post.entity.Post;
import com.tenure.global.pagination.Cursor;
import java.util.List;

/**
 * 조건이 검색어에 따라 갈리는 목록 조회. 정렬은 화면 규칙이라 쿼리에 박는다.
 *
 * size + 1 건을 돌려준다 — 한 건 더 읽는 것으로 다음 페이지 유무를 알 수 있어 전체를 세지 않는다.
 *
 * 응답 조립에 필요한 최근 착용일과 판매 등록은 여기서 채우지 않는다 — 아이템마다 조회하면
 * 행 수만큼 쿼리가 늘어난다. Service 가 페이지의 id 목록으로 한 번에 가져와 맞춘다.
 */
public interface ItemRepositoryCustom {

	/**
	 * 내 옷장. 등록자가 아니라 현재 소유자 기준이다 — 판 아이템은 목록에서 빠져야
	 * 행을 눌러 들어간 상세에서 수정·삭제가 막히는 일이 없다.
	 *
	 * 커서 키는 등록일이다.
	 */
	List<Item> findOwnedItems(Long ownerId, String keyword, Cursor cursor, int size);
	/**
	 * 아이템 기준 추천에 쓸 최근 게시물. 블록마다 후보 아이템을 고르는 규칙만 다르다.
	 *
	 * 유사도 모델을 쓰지 않는다 — 카테고리와 공동 태그로 후보를 좁히고 최신순으로 자른다.
	 * 차단 관계(양방향)와 삭제된 게시물·아이템은 빠진다.
	 */
	List<Post> findRecommendedPosts(Long viewerId, Item item, RecommendationBlock block, int limit);
}
