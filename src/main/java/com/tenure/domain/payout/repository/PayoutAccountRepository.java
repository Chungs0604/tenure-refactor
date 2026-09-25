package com.tenure.domain.payout.repository;

import com.tenure.domain.payout.entity.PayoutAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutAccountRepository extends JpaRepository<PayoutAccount, Long> {
}
