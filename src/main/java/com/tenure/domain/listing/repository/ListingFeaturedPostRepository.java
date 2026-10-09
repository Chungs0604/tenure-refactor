package com.tenure.domain.listing.repository;

import com.tenure.domain.listing.entity.ListingFeaturedPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;

public interface ListingFeaturedPostRepository extends JpaRepository<ListingFeaturedPost, Long> {

	/**
	 * 판매 중인 아이템의 대표 OOTD 로 걸려 있는지.
	 *
	 * 걸려 있으면 게시물을 지울 수 없다 — 대표 OOTD 는 1개 이상이어야 하므로 지우는 순간
	 * 판매 화면의 그 칸이 비어 버린다. 판매가 끝난 등록의 대표 OOTD 는 지울 수 있다.
	 *
	 * 판매 상태는 아이템이 들고 있으므로 판매 등록을 거쳐 아이템까지 탄다 (DEC-026).
	 * 닫힌 판매 등록은 제외한다 — 그 등록의 대표 OOTD 는 더 이상 화면에 뜨지 않는다.
	 */
	@Query("select count(f) > 0 from ListingFeaturedPost f "
			+ "where f.post.id = :postId and f.listing.closedAt is null "
			+ "and f.listing.item.saleStatus = com.tenure.domain.item.enums.SaleStatus.ON_SALE")
	boolean existsOnSaleByPostId(@Param("postId") Long postId);

	/**
	 * 판매 등록의 대표 OOTD. 선택 순서대로 그린다.
	 *
	 * 게시물을 함께 읽는다 — 상세가 사진과 비율을 그리므로 행마다 다시 조회하면 N+1 이 된다.
	 */
	@Query("select f from ListingFeaturedPost f join fetch f.post "
			+ "where f.listing.id = :listingId order by f.sortOrder")
	List<ListingFeaturedPost> findAllByListingId(@Param("listingId") Long listingId);

	/** 전체 교체라 비우고 다시 넣는다. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from ListingFeaturedPost f where f.listing.id = :listingId")
	void deleteAllByListingId(@Param("listingId") Long listingId);
}
