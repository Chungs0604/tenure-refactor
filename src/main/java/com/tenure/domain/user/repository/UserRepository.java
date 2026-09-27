package com.tenure.domain.user.repository;

import com.tenure.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	// MySQL 기본 collation(utf8mb4_0900_ai_ci)이 대소문자를 무시하므로
	// 별도 정규화 없이 Sujun 과 sujun 이 같은 값으로 걸린다
	boolean existsByEmail(String email);

	boolean existsByNickname(String nickname);

	Optional<User> findByEmail(String email);

	/** 프로필 수정용. 자기 행을 빼고 보므로 대소문자 판정을 DB collation 에 맡길 수 있다. */
	@Query("select count(u) > 0 from User u " +
			"where u.nickname = :nickname " +
			"and u.id <> :userId")
	boolean existsOtherNickname(
			@Param("nickname") String nickname,
			@Param("userId") Long userId
	);
}
