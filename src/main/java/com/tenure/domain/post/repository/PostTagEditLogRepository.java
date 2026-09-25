package com.tenure.domain.post.repository;

import com.tenure.domain.post.entity.PostTagEditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostTagEditLogRepository extends JpaRepository<PostTagEditLog, Long> {
}
