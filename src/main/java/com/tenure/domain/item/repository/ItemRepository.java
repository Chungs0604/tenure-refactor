package com.tenure.domain.item.repository;

import com.tenure.domain.item.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, Long> {

	/** 프로필의 '아이템 16'. 등록한 아이템 목록 화면과 조건이 같아야 수치와 목록이 어긋나지 않는다. */
	@Query("select count(i) from Item i " +
			"where i.registrant.id = :registrantId " +
			"and i.deletedAt is null")
	int countRegisteredItem(@Param("registrantId") Long registrantId);
}
