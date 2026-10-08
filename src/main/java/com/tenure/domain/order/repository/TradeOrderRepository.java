package com.tenure.domain.order.repository;

import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.order.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long>, TradeOrderRepositoryCustom {

	/** 상세 조회(읽기 전용) — 연관 엔티티를 함께 읽어 화면 조립에 추가 조회가 없게 함 */
	@Query("select o from TradeOrder o "
			+ "join fetch o.item join fetch o.buyer join fetch o.seller join fetch o.offer "
			+ "left join fetch o.listing "
			+ "where o.id = :orderId")
	Optional<TradeOrder> findDetailById(@Param("orderId") Long orderId);

	/**
	 * 상태 전이 전용 — 이 거래 행만 잠금. 연관 엔티티는 조인하지 않음 — PurchaseOfferRepository
	 * 와 같은 이유로, 조인하면 관련 없는 행까지 묶여 잠기고 잠금 순서도 뒤섞인다
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from TradeOrder o where o.id = :orderId")
	Optional<TradeOrder> findByIdForUpdate(@Param("orderId") Long orderId);

	/**
	 * 아직 끝나지 않은 거래가 있는지. 아이템 삭제를 막는 판정이다.
	 *
	 * 끝난 상태(거래 완료·거래 취소)만 빼고 본다 — 새 상태가 중간에 생겨도 자동으로 '진행 중' 에
	 * 포함되도록, 진행 중 목록을 나열하는 대신 종료 상태를 제외한다.
	 */
	@Query("select count(o) > 0 from TradeOrder o " +
			"where o.item.id = :itemId " +
			"and o.status not in :finishedStatuses")
	boolean existsInProgress(
			@Param("itemId") Long itemId,
			@Param("finishedStatuses") Collection<OrderStatus> finishedStatuses
	);

	/**
	 * 아직 끝나지 않은 거래가 있는지 — 사용자 기준(구매자·판매자 어느 쪽이든). 회원탈퇴를 막는 판정에 씀.
	 * existsInProgress 와 같은 이유로 종료 상태만 제외함
	 */
	@Query("select count(o) > 0 from TradeOrder o " +
			"where (o.buyer.id = :userId or o.seller.id = :userId) " +
			"and o.status not in :finishedStatuses")
	boolean existsInProgressByUser(
			@Param("userId") Long userId,
			@Param("finishedStatuses") Collection<OrderStatus> finishedStatuses
	);

	/** 자동 구매 확정 배치 대상 id 목록. id 만 돌려주는 이유는 PurchaseOfferRepository 와 같다 */
	@Query("select o.id from TradeOrder o where o.status = 'DELIVERED' and o.autoConfirmAt <= :now")
	List<Long> findAutoConfirmableIds(@Param("now") LocalDateTime now);

	/** 이 제안이 수락되어 생긴 거래. 채팅방 헤더 CTA(거래 상세) 의 targetId 근거 */
	Optional<TradeOrder> findByOfferId(Long offerId);
}
