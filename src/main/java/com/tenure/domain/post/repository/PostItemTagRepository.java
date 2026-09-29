package com.tenure.domain.post.repository;

import com.tenure.domain.post.entity.PostItemTag;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostItemTagRepository extends JpaRepository<PostItemTag, Long> {

	/** 태그된 아이템 목록(SCR-016). 단위가 게시물이 아니라 사진이다 (DEC-104). */
	@Query("select t from PostItemTag t join fetch t.item " +
			"where t.postImage.id = :imageId " +
			"order by t.id")
	List<PostItemTag> findAllByPostImageId(@Param("imageId") Long imageId);

	/** 상세의 말풍선. 사진 순서대로, 사진 안에서는 부착 순서대로 그린다. */
	@Query("select t from PostItemTag t join fetch t.item join fetch t.postImage i " +
			"where i.post.id = :postId " +
			"order by i.sortOrder, t.id")
	List<PostItemTag> findAllByPostId(@Param("postId") Long postId);
}
