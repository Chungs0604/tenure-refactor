package com.tenure.domain.item.repository;

import com.tenure.domain.item.entity.Item;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, Long> {

	/** 프로필의 '아이템 16'. 등록한 아이템 목록 화면과 조건이 같아야 수치와 목록이 어긋나지 않는다. */
	@Query("select count(i) from Item i " +
			"where i.registrant.id = :registrantId " +
			"and i.deletedAt is null")
	int countRegisteredItem(@Param("registrantId") Long registrantId);

	/** 태그 대상 아이템. 삭제된 아이템에는 새 태그를 붙일 수 없다. */
	@Query("select i from Item i where i.id = :itemId and i.deletedAt is null")
	Optional<Item> findActive(@Param("itemId") Long itemId);

	/** 태그된 아이템 목록·상세 말풍선. 삭제된 아이템도 화면에 회색으로 남으므로 함께 받는다. */
	@Query("select i from Item i where i.id in :itemIds")
	List<Item> findAllByIds(@Param("itemIds") Collection<Long> itemIds);
}
