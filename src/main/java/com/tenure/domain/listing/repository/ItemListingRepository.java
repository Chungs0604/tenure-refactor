package com.tenure.domain.listing.repository;

import com.tenure.domain.listing.entity.ItemListing;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemListingRepository extends JpaRepository<ItemListing, Long> {

	/**
	 * 아이템별 열린 판매 등록. 지금 걸려 있는 판매 조건이다.
	 *
	 * 판매를 내리면 closed_at 을 찍고 다시 걸 때 새 행을 만들므로(DEC-026) 열린 행은
	 * 아이템당 최대 하나다. 없으면 키가 없다 — 판매 전환을 한 번도 안 했거나 판매를 내린 상태다.
	 *
	 * 판매 상태 자체는 item.sale_status 가 들고 있다. 이 조회는 가격·배송비처럼
	 * 판매 등록에만 있는 값을 그릴 때 쓴다.
	 */
	@Query("select l from ItemListing l where l.item.id in :itemIds and l.closedAt is null")
	List<ItemListing> findOpenByItemIds(@Param("itemIds") Collection<Long> itemIds);

	/** 아이템 하나의 열린 판매 등록. */
	@Query("select l from ItemListing l where l.item.id = :itemId and l.closedAt is null")
	Optional<ItemListing> findOpenByItemId(@Param("itemId") Long itemId);

	/**
	 * 아이템의 가장 최근 판매 등록. 닫힌 것도 포함한다.
	 *
	 * 판매 전환 폼의 빈칸을 직전 조건으로 채울 때 쓴다 — '아이템에 관하여 이미 등록된 정보가
	 * 있다면 미리 빈칸에 작성되어있습니다'(안내사항 3633:34232).
	 */
	@Query("select l from ItemListing l join fetch l.seller "
			+ "where l.item.id = :itemId order by l.listedAt desc, l.id desc limit 1")
	Optional<ItemListing> findLatestByItemId(@Param("itemId") Long itemId);

	/**
	 * 여러 아이템의 가장 최근 판매 등록(닫힌 것 포함)을 한 번에 읽음. 위시리스트 목록처럼
	 * 페이지마다 아이템이 여럿인 곳에서 건마다 findLatestByItemId 를 부르면 N+1 이 됨.
	 *
	 * 아이템당 여러 행이 섞여 오므로, 아이템 안에서 최신 한 건만 남기는 일은 호출하는 쪽이
	 * item.id 로 묶어 첫 행만 취하는 방식으로 함(정렬이 이미 최신순이라 그룹의 첫 행이 최신).
	 */
	@Query("select l from ItemListing l join fetch l.seller "
			+ "where l.item.id in :itemIds order by l.item.id asc, l.listedAt desc, l.id desc")
	List<ItemListing> findAllByItemIdsOrderByLatest(@Param("itemIds") Collection<Long> itemIds);
}
