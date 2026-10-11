package com.tenure.domain.item.repository;

import com.tenure.domain.item.entity.Item;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

	/**
	 * 탈퇴 시 정리 대상(현재 소유한 판매중 아이템) id 목록. id 만 가볍게 읽고 건마다 잠금
	 * 재조회로 다시 확인한다 — order by 는 여러 건을 동시에 잠그는 다른 경로와 잠금 순서를
	 * 맞추기 위함임(findActiveForUpdate 참고)
	 */
	@Query("select i.id from Item i "
			+ "where i.deletedAt is null and i.saleStatus = 'ON_SALE' "
			+ "and exists (select 1 from ItemOwnership o "
			+ "            where o.item.id = i.id and o.owner.id = :ownerId and o.endDate is null) "
			+ "order by i.id")
	List<Long> findOnSaleItemIdsByCurrentOwner(@Param("ownerId") Long ownerId);

	/** 태그 대상 아이템. 삭제된 아이템에는 새 태그를 붙일 수 없다. */
	@Query("select i from Item i " +
			"where i.id = :itemId and i.deletedAt is null")
	Optional<Item> findActive(@Param("itemId") Long itemId);

	/**
	 * 아이템 상세. 카테고리와 그 부모를 함께 읽는다 —
	 * 화면이 '상의 · 긴소매 티셔츠' , 사이즈 함께 사용
	 */
	@Query("select i from Item i join fetch i.category c join fetch c.parent " +
			"where i.id = :itemId and i.deletedAt is null")
	Optional<Item> findActiveWithCategory(@Param("itemId") Long itemId);

	/** 태그된 아이템 목록·상세 말풍선. 삭제된 아이템도 화면에 회색으로 남으므로 함께 받는다. */
	@Query("select i from Item i " +
			"where i.id in :itemIds")
	List<Item> findAllByIds(@Param("itemIds") Collection<Long> itemIds);

	/**
	 * 행 잠금 조회. 같은 아이템의 서로 다른 제안 두 건이 동시에 수락되는 것을 막는 용도임 —
	 * 커밋된 값을 보장받아야 '이미 판매 완료' 재확인이 의미를 가짐
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Item i where i.id = :itemId and i.deletedAt is null")
	Optional<Item> findActiveForUpdate(@Param("itemId") Long itemId);

	/**
	 * 행 잠금 조회. 삭제 여부를 가리지 않음 — 관심 해제는 그 사이 아이템이 삭제됐어도
	 * wish_count 를 맞춰야 하고, 삭제된 아이템에 새로 관심을 등록할 길은 findActiveForUpdate 가
	 * 막아서 여기서 다시 가릴 필요가 없음
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Item i where i.id = :itemId")
	Optional<Item> findForUpdate(@Param("itemId") Long itemId);
}
