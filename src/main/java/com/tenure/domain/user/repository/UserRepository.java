package com.tenure.domain.user.repository;

import com.tenure.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	// MySQL 기본 collation(utf8mb4_0900_ai_ci)이 대소문자를 무시하므로
	// 별도 정규화 없이 Sujun 과 sujun 이 같은 값으로 걸린다
	boolean existsByEmail(String email);

	boolean existsByNickname(String nickname);

	Optional<User> findByEmail(String email);
}
