package com.tenure.domain.search.repository;

import com.tenure.domain.search.entity.RecentView;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecentViewRepository extends JpaRepository<RecentView, Long> {
}
