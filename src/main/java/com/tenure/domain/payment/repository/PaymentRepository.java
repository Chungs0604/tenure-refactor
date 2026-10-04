package com.tenure.domain.payment.repository;

import com.tenure.domain.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	/** 제안당 결제 1건. uk_payment_offer 가 보장함 */
	Optional<Payment> findByOfferId(Long offerId);

	/** 확정·해제 전용 — 행 잠금 조회임. 같은 결제에 두 전이가 동시에 들어오는 경합을 막음 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.offer.id = :offerId")
	Optional<Payment> findByOfferIdForUpdate(@Param("offerId") Long offerId);
}
