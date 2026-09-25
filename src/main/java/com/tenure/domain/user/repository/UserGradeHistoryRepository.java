package com.tenure.domain.user.repository;

import com.tenure.domain.user.entity.UserGradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGradeHistoryRepository extends JpaRepository<UserGradeHistory, Long> {
}
