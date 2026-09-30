package com.tenure.domain.item.repository;

import com.tenure.domain.item.entity.Item;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, Long>, ItemRepositoryCustom {

	/**
	 * 프로필의 '아이템 16'. 등록한 아이템 목록 화면과 조건이 같아야 수치와 목록이 어긋나지 않는다.
	 *
	 * 등록자가 아니라 현재 소유자 기준이다 — 목록이 그렇게 갈리므로 여기도 같이 간다.
	 * 아이템을 팔면 카운터에서도 빠진다.
	 */
	@Query("select count(i) from Item i "
			+ "where i.deletedAt is null "
			+ "and exists (select 1 from ItemOwnership o "
			+ "            where o.item.id = i.id and o.owner.id = :ownerId and o.endDate is null)")
	int countOwnedItem(@Param("ownerId") Long ownerId);

	/** 태그 대상 아이템. 삭제된 아이템에는 새 태그를 붙일 수 없다. */
	@Query("select i from Item i " +
			"where i.id = :itemId and i.deletedAt is null")
	Optional<Item> findActive(@Param("itemId") Long itemId);

	/**
	 * 아이템 상세. 카테고리와 그 부모를 함께 읽는다 —
	 * 화면이 '상의 · 긴소매 티셔츠' , 사이즈 함께 사용
	 */
	@Query("select i from Item i join fetch i.category c join fetch c.parent "
			+ "where i.id = :itemId and i.deletedAt is null")
	Optional<Item> findActiveWithCategory(@Param("itemId") Long itemId);

	/** 태그된 아이템 목록·상세 말풍선. 삭제된 아이템도 화면에 회색으로 남으므로 함께 받는다. */
	@Query("select i from Item i " +
			"where i.id in :itemIds")
	List<Item> findAllByIds(@Param("itemIds") Collection<Long> itemIds);
}
