package com.tenure.domain.user.repository;

import com.tenure.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	// MySQL 기본 collation(utf8mb4_0900_ai_ci)이 대소문자를 무시하므로
	// 별도 정규화 없이 Sujun 과 sujun 이 같은 값으로 걸린다
	boolean existsByEmail(String email);

	boolean existsByNickname(String nickname);

	Optional<User> findByEmail(String email);

	/**
	 * User 행에 쓰기 잠금이 필요한 곳에서 공용으로 씀 — 잠그지 않으면 동시 요청이 서로의 갱신을
	 * 덮어쓰거나(lost update) MySQL 데드락이 그대로 새는 자리들이다.
	 *
	 * 탈퇴(UserService.withdraw()) — 같은 사용자가 동시에 탈퇴를 두 번 요청해도 뒤처진
	 * 트랜잭션이 user_withdrawal insert 까지 가지 않고 여기서 먼저 막히게 함.
	 *
	 * 팔로우(FollowService.follow()/unfollow()) — follower_count 증감을 보호함.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :userId")
	Optional<User> findByIdForUpdate(@Param("userId") Long userId);

	/** 프로필 수정용. 자기 행을 빼고 보므로 대소문자 판정을 DB collation 에 맡길 수 있다. */
	@Query("select count(u) > 0 from User u " +
			"where u.nickname = :nickname " +
			"and u.id <> :userId")
	boolean existsOtherNickname(
			@Param("nickname") String nickname,
			@Param("userId") Long userId
	);
}
