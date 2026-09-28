package com.tenure.domain.settlement.repository;

import com.tenure.domain.settlement.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

	/** 입금이 끝나지 않은 정산이 남아 있는지. 정산 계좌 삭제를 막는 데 쓴다. */
	boolean existsBySellerIdAndSettledAtIsNull(Long sellerId);
}
