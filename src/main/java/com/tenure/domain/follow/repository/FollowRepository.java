package com.tenure.domain.follow.repository;

import com.tenure.domain.follow.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, Long> {

	/** 타인 프로필의 팔로우 버튼 상태. */
	@Query("select count(f) > 0 from Follow f " +
			"where f.follower.id = :followerId " +
			"and f.following.id = :followingId")
	boolean existsFollow(
			@Param("followerId") Long followerId,
			@Param("followingId") Long followingId
	);
}
