package com.tenure.domain.post.repository;

import com.tenure.domain.post.entity.PostSave;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostSaveRepository extends JpaRepository<PostSave, Long> {

	boolean existsByPostIdAndUserId(Long postId, Long userId);

	Optional<PostSave> findByPostIdAndUserId(Long postId, Long userId);

	/** 목록의 북마크 아이콘 상태. */
	@Query("select s.post.id from PostSave s " +
			"where s.user.id = :userId " +
			"and s.post.id in :postIds")
	List<Long> findSavedPostIds(
			@Param("userId") Long userId,
			@Param("postIds") Collection<Long> postIds
	);
}
