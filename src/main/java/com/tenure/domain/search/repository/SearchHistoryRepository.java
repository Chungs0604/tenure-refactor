package com.tenure.domain.search.repository;

import com.tenure.domain.search.entity.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long>, SearchHistoryRepositoryCustom {

	/** 회원탈퇴 시 하드 삭제. 검색 이력은 다시 볼 일도 남길 이유도 없음 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from SearchHistory s where s.user.id = :userId")
	void deleteAllByUserId(@Param("userId") Long userId);
}
