package com.tenure.domain.user.repository;

import com.tenure.domain.user.entity.UserWithdrawal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWithdrawalRepository extends JpaRepository<UserWithdrawal, Long> {
}
