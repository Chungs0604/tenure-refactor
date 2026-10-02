package com.tenure.domain.offer.repository;

import com.tenure.domain.offer.entity.PurchaseOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import com.tenure.domain.offer.enums.OfferStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOfferRepository extends JpaRepository<PurchaseOffer, Long> {

	/**
	 * 내가 이 아이템에 보낸 제안의 상태. 상세 CTA 가 이 값으로 갈린다.
	 *
	 * 아이템당 1회 제한이라 최대 한 건이지만, 거절·취소 뒤 재도전이 열리면 여러 건이 될 수 있어
	 * 최신 한 건만 본다.
	 */
	@Query("select o.status from PurchaseOffer o "
			+ "where o.item.id = :itemId and o.buyer.id = :buyerId "
			+ "order by o.id desc limit 1")
	Optional<OfferStatus> findLatestStatus(@Param("itemId") Long itemId, @Param("buyerId") Long buyerId);
}
