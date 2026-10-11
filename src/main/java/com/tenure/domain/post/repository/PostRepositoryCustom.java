package com.tenure.domain.post.repository;

import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.enums.FeedTab;
import com.tenure.domain.post.enums.MyPostTab;
import com.tenure.global.pagination.Cursor;
import java.time.YearMonth;
import java.util.List;

/**
 * 조건이 탭·월 선택에 따라 갈리는 목록 조회. 정렬은 화면 규칙이라 쿼리에 박는다.
 *
 * size + 1 건을 돌려준다. 한 건 더 읽는 것으로 다음 페이지 유무를 알 수 있어
 * 전체를 세지 않는다 — 화면에 총 건수를 쓰는 목록이 없으므로 count 쿼리는 낭비다.
 *
 * 응답 조립에 필요한 대표 사진과 좋아요·저장 여부는 여기서 채우지 않는다 — 게시물 한 건마다
 * 조회하면 카드 수만큼 쿼리가 늘어난다. Service 가 페이지의 id 목록으로 한 번에 가져와 맞춘다.
 */
public interface PostRepositoryCustom {

	/** 홈 피드. 차단 관계(양방향)를 제외한다. 커서 키는 게시일이다. */
	List<Post> findFeed(Long viewerId, FeedTab tab, Cursor cursor, int size);

	/** 마이페이지의 게시물 · 좋아요 · 저장 탭. 월을 고르면 그 달만 본다. 커서 키는 착용일이다. */
	List<Post> findMyPosts(Long userId, MyPostTab tab, YearMonth yearMonth, Cursor cursor, int size);

	/** 타인 프로필의 게시물 그리드. 커서 키는 착용일이다. */
	List<Post> findUserPosts(Long authorId, Cursor cursor, int size);

	/** 조회수 배치 반영 (DEC-019). 대상 게시물이 삭제됐으면 조용히 무시한다 — 조회수는 다시 볼 일이 없다. */
	void incrementViewCount(Long postId, long delta);
}
