package com.tenure.domain.post.repository;

import com.tenure.domain.post.entity.PostImage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

	/** 상세의 사진 캐러셀. 순서는 사용자가 올린 순서다. */
	@Query("select i from PostImage i where i.post.id = :postId order by i.sortOrder")
	List<PostImage> findAllByPostId(@Param("postId") Long postId);

	/**
	 * 목록 카드·그리드의 대표 사진.
	 *
	 * 게시물마다 한 번씩 조회하면 카드 수만큼 쿼리가 늘어나므로 페이지의 id 를 모아 한 번에 받는다.
	 */
	@Query("select i from PostImage i " +
			"where i.post.id in :postIds " +
			"and i.isCover = true")
	List<PostImage> findCovers(@Param("postIds") Collection<Long> postIds);

	/** 태그된 아이템 목록의 진입점. 삭제된 게시물의 사진은 대상이 아니다. */
	@Query("select i from PostImage i join fetch i.post p "
			+ "where i.id = :imageId and p.deletedAt is null")
	Optional<PostImage> findActiveWithPost(@Param("imageId") Long imageId);
}
