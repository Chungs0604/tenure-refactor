package com.tenure.domain.post.repository;

import com.tenure.domain.post.entity.PostLike;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

	boolean existsByPostIdAndUserId(Long postId, Long userId);

	Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

	/** 목록의 하트 아이콘 상태. 카드마다 묻지 않고 페이지의 id 를 모아 한 번에 받는다. */
	@Query("select l.post.id from PostLike l " +
			"where l.user.id = :userId " +
			"and l.post.id in :postIds")
	List<Long> findLikedPostIds(
			@Param("userId") Long userId,
			@Param("postIds") Collection<Long> postIds
	);
}
