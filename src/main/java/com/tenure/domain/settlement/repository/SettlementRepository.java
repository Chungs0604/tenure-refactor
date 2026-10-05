package com.tenure.domain.settlement.repository;

import com.tenure.domain.settlement.entity.Settlement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

	/** 입금이 끝나지 않은 정산이 남아 있는지. 정산 계좌 삭제를 막는 데 쓴다. */
	boolean existsBySellerIdAndSettledAtIsNull(Long sellerId);

	/** 거래당 1건. 거래 상세의 정산 정보(SELLER 시점) */
	Optional<Settlement> findByOrderId(Long orderId);

	/** 정산 배치 대상. 입금이 끝나지 않은 건 전부 — 영업일 도달 여부는 배치가 매번 다시 계산한다 */
	@Query("select s from Settlement s join fetch s.order where s.settledAt is null")
	List<Settlement> findAllBySettledAtIsNull();
}
