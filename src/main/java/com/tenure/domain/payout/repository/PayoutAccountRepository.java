package com.tenure.domain.payout.repository;

import com.tenure.domain.payout.entity.PayoutAccount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayoutAccountRepository extends JpaRepository<PayoutAccount, Long> {

	/** 목록 정렬은 화면 규칙이라 쿼리에 박는다 — 기본 계좌가 맨 위, 그다음 최근 등록 순. */
	@Query("select p from PayoutAccount p where p.user.id = :userId "
			+ "order by p.isDefault desc, p.createdAt desc")
	List<PayoutAccount> findAllOf(@Param("userId") Long userId);

	/** 첫 계좌인지 판정한다. 첫 계좌는 고를 여지가 없어 기본이 된다. */
	boolean existsByUserId(Long userId);

	/**
	 * 기존 기본 계좌를 내린다.
	 *
	 * 엔티티를 고쳐서 내리지 않는 이유 — Hibernate 는 flush 할 때 INSERT 를 UPDATE 보다 먼저
	 * 실행한다. 그러면 새 기본 계좌가 먼저 들어가 uk_payout_account_default 에 걸린다.
	 * 벌크 UPDATE 는 호출 즉시 DB 에 닿으므로 해제가 항상 앞선다.
	 */
	@Modifying
	@Query("update PayoutAccount p set p.isDefault = false "
			+ "where p.user.id = :userId and p.isDefault = true")
	int releaseDefault(@Param("userId") Long userId);
}
