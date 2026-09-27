package com.tenure.domain.block.repository;

import com.tenure.domain.block.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

	/** 타인 프로필의 차단하기 / 차단해제 토글 상태. */
	@Query("select count(b) > 0 from UserBlock b "
			+ "where b.blocker.id = :blockerId and b.blocked.id = :blockedId")
	boolean existsBlock(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);
}
