package com.tenure.domain.post.repository;

import com.tenure.domain.post.entity.Post;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {

	/** 상세·수정·삭제의 진입점. 삭제된 게시물은 없는 것으로 본다. */
	@Query("select p from Post p where p.id = :postId and p.deletedAt is null")
	Optional<Post> findActive(@Param("postId") Long postId);

	/** 상세는 작성자 정보를 함께 그린다 — 닉네임·프로필 사진·팔로워 수. */
	@Query("select p from Post p join fetch p.author where p.id = :postId and p.deletedAt is null")
	Optional<Post> findActiveWithAuthor(@Param("postId") Long postId);

	/**
	 * 레코드 승격 판정의 '유효 OOTD' 수.
	 *
	 * 유효 = 삭제되지 않았고 아이템 태그가 1개 이상. 기준 날짜는 착용일이 아니라 게시일이다 —
	 * 착용일 기준이면 과거 날짜로 소급 게시해 수치를 만들 수 있다.
	 *
	 * 태그 보유 여부를 컬럼으로 두지 않는다 (DEC-002). 판정 범위가 한 사용자의 30일로 좁고
	 * ix_post_author_created 가 받아준다.
	 */
	@Query("select count(p) from Post p "
			+ "where p.author.id = :authorId "
			+ "and p.deletedAt is null "
			+ "and p.createdAt >= :from "
			+ "and exists (select 1 from PostItemTag t where t.postImage.post = p)")
	int countValidOotd(@Param("authorId") Long authorId, @Param("from") LocalDateTime from);

	/** 대표 OOTD 로 걸 게시물. 삭제된 것은 빠지므로 개수로 존재를 확인한다. */
	@Query("select p from Post p where p.id in :postIds and p.deletedAt is null")
	List<Post> findActiveByIds(@Param("postIds") Collection<Long> postIds);
}
