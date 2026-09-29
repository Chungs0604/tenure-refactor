package com.tenure.domain.listing.repository;

import com.tenure.domain.listing.entity.ItemListing;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemListingRepository extends JpaRepository<ItemListing, Long> {

	/**
	 * 아이템별 최신 판매 등록.(현재 아이템의 판매 여부)
	 *
	 * 아이템은 재판매될 때마다 등록 행이 새로 생기므로(1:N) 화면에 쓰는 것은 가장 최근 한 건이다.
	 * 상세 말풍선의 '#판매중' 표기와 태그된 아이템 목록의 CTA 판정이 이 값을 본다.
	 */
	@Query("select l from ItemListing l where l.item.id in :itemIds and l.id in "
			+ "(select max(latest.id) from ItemListing latest "
			+ "where latest.item.id in :itemIds group by latest.item.id)")
	List<ItemListing> findLatestByItemIds(@Param("itemIds") Collection<Long> itemIds);
	/** 삭제를 막는 판정. 최신 등록이 판매 중이면 지울 수 없다. */
	@Query("select count(l) > 0 from ItemListing l "
			+ "where l.item.id = :itemId and l.saleStatus = com.tenure.domain.listing.enums.SaleStatus.ON_SALE")
	boolean existsOnSale(@Param("itemId") Long itemId);
}
