package com.tenure.domain.block.repository;

import com.tenure.domain.block.entity.UserBlock;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

	/** 타인 프로필의 차단하기 / 차단해제 토글 상태. */
	@Query("select count(b) > 0 from UserBlock b "
			+ "where b.blocker.id = :blockerId and b.blocked.id = :blockedId")
	boolean existsBlock(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

	/** 차단해제용. 삭제할 관계 자체를 찾아야 해서 existsBlock 과 별도로 둔다 */
	Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

	/**
	 * 양방향 차단 여부. 둘 중 누가 먼저 차단했는지는 호출부 대부분에서 중요하지 않아,
	 * 각자 existsBlock 을 두 번 호출해 OR 로 묶던 걸 쿼리 하나로 모은다.
	 */
	@Query("select count(b) > 0 from UserBlock b "
			+ "where (b.blocker.id = :userId1 and b.blocked.id = :userId2) "
			+ "or (b.blocker.id = :userId2 and b.blocked.id = :userId1)")
	boolean existsMutualBlock(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
