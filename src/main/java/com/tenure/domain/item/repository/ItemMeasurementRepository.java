package com.tenure.domain.item.repository;

import com.tenure.domain.item.entity.ItemMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemMeasurementRepository extends JpaRepository<ItemMeasurement, Long> {

	/** 아이템당 한 행이다 — 실측은 아이템 속성이고 재판매해도 이어진다. */
	@Query("select m from ItemMeasurement m where m.item.id = :itemId")
	Optional<ItemMeasurement> findByItemId(@Param("itemId") Long itemId);

	/**
	 * 교체 전에 비운다. 벌크 삭제라 영속성 컨텍스트를 우회한다 — 뒤이어 같은 트랜잭션에서
	 * save 로 새 행을 넣으므로 clear 가 필요하고, DELETE 가 즉시 DB 에 닿아야 다음 INSERT 가
	 * uk_item_measurement_item 과 부딪히지 않는다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from ItemMeasurement m where m.item.id = :itemId")
	void deleteByItemId(@Param("itemId") Long itemId);
}
