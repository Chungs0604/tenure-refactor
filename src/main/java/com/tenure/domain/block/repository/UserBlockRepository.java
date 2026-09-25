package com.tenure.domain.block.repository;

import com.tenure.domain.block.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
}
