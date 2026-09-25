package com.tenure.domain.item.repository;

import com.tenure.domain.item.entity.ItemOwnership;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemOwnershipRepository extends JpaRepository<ItemOwnership, Long> {
}
