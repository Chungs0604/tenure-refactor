package com.tenure.domain.listing.repository;

import com.tenure.domain.listing.entity.ListingFeaturedPost;
import com.tenure.domain.item.enums.SaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
			+ "and f.listing.item.saleStatus = :saleStatus")
	boolean existsByPostIdAndSaleStatus(
			@Param("postId") Long postId, @Param("saleStatus") SaleStatus saleStatus
	);
}
