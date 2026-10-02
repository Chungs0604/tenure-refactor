package com.tenure.domain.item.repository;

import com.tenure.domain.item.entity.ItemMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemMeasurementRepository extends JpaRepository<ItemMeasurement, Long> {

	/** 아이템당 한 행이다 — 실측은 아이템 속성이고 재판매해도 이어진다. */
	@Query("select m from ItemMeasurement m where m.item.id = :itemId")
	Optional<ItemMeasurement> findByItemId(@Param("itemId") Long itemId);
}
