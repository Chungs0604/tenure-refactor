package com.tenure.domain.offer.repository;

import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.offer.enums.OfferStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

	/** 이 구매자가 이 아이템에 보낸 가장 최근 제안 한 건. PURCHASE_OFFER 1회 제한 판정에 씀 */
	@Query("select o from PurchaseOffer o "
			+ "where o.item.id = :itemId and o.buyer.id = :buyerId "
			+ "order by o.id desc limit 1")
	Optional<PurchaseOffer> findLatestByItemIdAndBuyerId(@Param("itemId") Long itemId, @Param("buyerId") Long buyerId);

	/** 상세 조회(읽기 전용) — 연관 엔티티를 함께 읽어 화면 조립에 추가 조회가 없게 함 */
	@Query("select o from PurchaseOffer o "
			+ "join fetch o.item i join fetch o.buyer join fetch o.seller "
			+ "left join fetch o.listing "
			+ "where o.id = :offerId")
	Optional<PurchaseOffer> findDetailById(@Param("offerId") Long offerId);

	/** 이 제안이 걸린 아이템 id. 잠금 순서(item → offer)를 지키려면 offer 를 잠그기 전에 알아야 함 */
	@Query("select o.item.id from PurchaseOffer o where o.id = :offerId")
	Optional<Long> findItemIdByOfferId(@Param("offerId") Long offerId);

	/**
	 * 수락·거절·취소·만료 전용 — 이 제안 행만 잠금. 연관 엔티티는 조인하지 않음 — 조인하면
	 * MySQL 이 item·item_listing·user 행까지 함께 잠가 관련 없는 다른 제안까지 불필요하게
	 * 직렬화되고, 다른 경로와 잠금 순서가 뒤섞여 교착의 소지가 생김
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from PurchaseOffer o where o.id = :offerId")
	Optional<PurchaseOffer> findByIdForUpdate(@Param("offerId") Long offerId);

	/** 받은 제안 목록(읽기 전용) 전용 — 연관 엔티티를 함께 읽어 추가 조회가 없게 함 */
	@Query("select o from PurchaseOffer o "
			+ "join fetch o.buyer "
			+ "where o.item.id = :itemId and o.status = 'PENDING' "
			+ "order by o.id desc")
	List<PurchaseOffer> findPendingByItemId(@Param("itemId") Long itemId);

	/**
	 * 같은 아이템의 대기 중인 제안 id 목록. 일부러 엔티티가 아니라 id 만 돌려줌 — 엔티티로
	 * 읽으면 그 순간 영속성 컨텍스트에 올라가, 뒤이어 잠금 재조회를 해도 Hibernate 가 이미 올라간
	 * 인스턴스를 그대로 돌려주고 최신 값으로 덮어쓰지 않는다(잠금 자체는 걸리지만 읽은 값이 낡음).
	 * 건마다 id 로만 다시 잠가야 재확인이 의미를 가짐
	 */
	@Query("select o.id from PurchaseOffer o where o.item.id = :itemId and o.status = 'PENDING'")
	List<Long> findPendingIdsByItemId(@Param("itemId") Long itemId);

	/** 만료 배치 대상 id 목록. 이유는 findPendingIdsByItemId 와 같음 */
	@Query("select o.id from PurchaseOffer o where o.status = 'PENDING' and o.expiresAt <= :now")
	List<Long> findExpirableIds(@Param("now") LocalDateTime now);
}
