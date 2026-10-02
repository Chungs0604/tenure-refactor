package com.tenure.domain.item.repository;

import com.tenure.domain.item.entity.ItemCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface ItemConditionRepository extends JpaRepository<ItemCondition, Long> {

	@Query("select c from ItemCondition c where c.item.id = :itemId")
	List<ItemCondition> findAllByItemId(@Param("itemId") Long itemId);

	/**
	 * 체크 목록 교체 전에 비운다. 코드마다 행이 하나씩이라 개별 비교보다 지우고 다시 넣는 편이 짧다.
	 *
	 * 벌크 삭제라 영속성 컨텍스트를 우회한다 — 같은 트랜잭션에서 다시 읽으므로 clear 가 필요하다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from ItemCondition c where c.item.id = :itemId")
	void deleteAllByItemId(@Param("itemId") Long itemId);
}
