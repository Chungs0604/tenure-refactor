package com.tenure.domain.follow.repository;

import com.tenure.domain.follow.entity.Follow;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, Long>, FollowRepositoryCustom {

	/** 타인 프로필의 팔로우 버튼 상태. */
	@Query("select count(f) > 0 from Follow f " +
			"where f.follower.id = :followerId " +
			"and f.following.id = :followingId")
	boolean existsFollow(
			@Param("followerId") Long followerId,
			@Param("followingId") Long followingId
	);

	/** 언팔로우용. 삭제할 관계 자체를 찾아야 해서 existsFollow 와 별도로 둔다 */
	Optional<Follow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

	/**
	 * 목록용 isFollowing 배치 조회. viewerId 가 candidateIds 중 실제로 팔로우하는 대상의 id 만 돌아온다
	 * — 행마다 existsFollow 를 부르면 N+1 이라 PostQueryService.likedPostIds() 와 같은 패턴으로 모은다.
	 */
	@Query("select f.following.id from Follow f " +
			"where f.follower.id = :viewerId " +
			"and f.following.id in :candidateIds")
	List<Long> findFollowingIdsAmong(
			@Param("viewerId") Long viewerId,
			@Param("candidateIds") Collection<Long> candidateIds
	);
}
