package com.tenure.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.tenure.domain.user.dto.request.UserWithdrawRequest;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.enums.UserStatus;
import com.tenure.domain.user.enums.WithdrawalReason;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.domain.user.repository.UserWithdrawalRepository;
import com.tenure.domain.search.store.RecentViewStore;
import com.tenure.global.security.jwt.WithdrawnUserStore;
import com.tenure.global.security.refresh.RefreshTokenStore;
import com.tenure.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * withdraw() 가 Redis 반영을 커밋 후로 미루는지 확인한다.
 *
 * <p>테스트 메서드를 {@code @Transactional} 로 두면 Spring Test 가 메서드 종료 뒤 항상 롤백한다 —
 * 즉 메서드 안에서는 커밋이 일어나지 않는다. withdraw() 는 기본 전파(REQUIRED) 라 이 트랜잭션에
 * 합류하므로, 메서드 안에서 Redis mock 이 전혀 불리지 않아야 afterCommit 으로 미뤄졌다는 뜻이다.
 * 커밋이 실패해 롤백되는 경우도 같은 메커니즘(커밋 전 미실행)이라 이 테스트로 함께 보장된다.
 */
@DisplayName("회원탈퇴 — Redis 반영 시점")
class UserWithdrawCommitTimingTest extends IntegrationTestSupport {

	@Autowired private UserService userService;
	@Autowired private UserRepository userRepository;
	@Autowired private UserWithdrawalRepository userWithdrawalRepository;

	@MockitoBean private RecentViewStore recentViewStore;
	@MockitoBean private RefreshTokenStore refreshTokenStore;
	@MockitoBean private WithdrawnUserStore withdrawnUserStore;

	private User user;

	@BeforeEach
	void setUp() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		user = userRepository.save(User.signUp(
				"commit-timing-%s@tenure.local".formatted(tag), "$2a$10$hashed", "홍길동",
				"821012345678", "ctiming-%s".formatted(tag), null, Gender.MALE, 170, 64));
	}

	@AfterEach
	void tearDown() {
		userWithdrawalRepository.findAll().stream()
				.filter(w -> w.getUser().getId().equals(user.getId()))
				.forEach(userWithdrawalRepository::delete);
		userRepository.deleteById(user.getId());
	}

	@Test
	@Transactional
	@DisplayName("트랜잭션이 아직 커밋되지 않은 시점에는 Redis 를 전혀 건드리지 않는다")
	void withdraw_beforeCommit_doesNotTouchRedis() {
		userService.withdraw(user.getId(), UserWithdrawRequest.builder().reason(WithdrawalReason.LOW_USAGE).build());

		// DB 반영은 같은 트랜잭션 안이라 바로 보인다
		User withdrawnInTx = userRepository.findById(user.getId()).orElseThrow();
		assertThat(withdrawnInTx.getStatus()).isEqualTo(UserStatus.WITHDRAWN);

		// 커밋은 이 테스트 메서드가 끝난 뒤 Spring Test 가 대신 롤백하므로 여기서는 아직
		// 일어나지 않았다 — afterCommit 으로 미룬 Redis 호출도 아직 실행되지 않아야 한다
		verifyNoInteractions(recentViewStore, refreshTokenStore, withdrawnUserStore);
	}
}
