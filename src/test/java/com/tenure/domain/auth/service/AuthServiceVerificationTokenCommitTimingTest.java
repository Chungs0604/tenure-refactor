package com.tenure.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.tenure.domain.auth.dto.request.PasswordResetRequest;
import com.tenure.domain.auth.enums.VerificationPurpose;
import com.tenure.domain.auth.store.EmailVerificationStore;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
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
 * {@code consumeVerificationToken()} 이 토큰을 DB 커밋 전까지 지우지 않는지 확인한다(이슈 e).
 *
 * <p>검증 패턴은 {@code UserWithdrawCommitTimingTest} 와 같다 — {@code @Transactional} 테스트
 * 메서드는 끝나면 Spring Test 가 항상 롤백하므로 메서드 안에서는 커밋이 일어나지 않는다. 그 시점에
 * {@code deleteToken} 이 전혀 불리지 않아야 afterCommit 으로 미뤄졌다는 뜻이다. DB 쓰기 자체가
 * 실패해 롤백되는 경우도 같은 메커니즘(커밋 전 미실행)이라 별도 테스트로 함께 확인한다.
 *
 * <p>{@link EmailVerificationStore} 를 모킹한다 — 이 프로젝트에 실제 Redis 에 연결하는 테스트
 * 패턴이 없다({@code HolidayStoreTest} 참고). 검증하려는 것은 Redis 호출 자체가 아니라 "커밋
 * 전까지는 지우는 호출이 나가지 않는다"는 트랜잭션 시점이라 모킹으로 충분하다.
 */
@DisplayName("비밀번호 재설정 — 인증 토큰 소비 시점")
class AuthServiceVerificationTokenCommitTimingTest extends IntegrationTestSupport {

	private static final String TOKEN = "d4f1c0ab9e7b4c2f8a5d3e6b1c9f0a2d";
	private static final String NEW_PASSWORD = "newPassword123";

	@Autowired private AuthService authService;
	@Autowired private UserRepository userRepository;

	@MockitoBean private EmailVerificationStore verificationStore;
	@MockitoBean private RefreshTokenStore refreshTokenStore;

	private User user;

	@BeforeEach
	void setUp() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		user = userRepository.save(User.signUp(
				"commit-timing-reset-%s@tenure.local".formatted(tag), "$2a$10$hashed", "홍길동",
				"821012345678", "rtiming-%s".formatted(tag), null, Gender.MALE, 170, 64));
	}

	@AfterEach
	void tearDown() {
		userRepository.deleteById(user.getId());
	}

	@Test
	@Transactional
	@DisplayName("트랜잭션이 아직 커밋되지 않은 시점에는 토큰을 지우지 않는다")
	void resetPassword_beforeCommit_doesNotDeleteToken() {
		givenValidToken(user.getEmail());

		authService.resetPassword(resetRequest(user.getEmail()));

		// DB 반영은 같은 트랜잭션 안이라 바로 보인다
		User updated = userRepository.findById(user.getId()).orElseThrow();
		assertThat(updated.getPasswordHash()).isNotEqualTo("$2a$10$hashed");

		// 커밋은 이 테스트 메서드가 끝난 뒤 Spring Test 가 대신 롤백하므로 아직 일어나지 않았다 —
		// afterCommit 으로 미룬 토큰 삭제도 아직 실행되지 않아야 한다
		verify(verificationStore, never()).deleteToken(TOKEN);
	}

	@Test
	@DisplayName("트랜잭션이 커밋되면 토큰을 지운다")
	void resetPassword_afterCommit_deletesToken() {
		givenValidToken(user.getEmail());

		// 테스트 메서드가 @Transactional 이 아니므로 resetPassword() 의 트랜잭션이 실제로 커밋된다
		authService.resetPassword(resetRequest(user.getEmail()));

		verify(verificationStore).deleteToken(TOKEN);
	}

	@Test
	@DisplayName("DB 쓰기가 실패해 롤백되면 토큰을 지우지 않는다 — 재발송 없이 재시도할 수 있다")
	void resetPassword_dbFailureRollsBack_tokenSurvives() {
		// 토큰은 유효하지만 대상 계정이 없다 — USER_404 로 끊겨 전체 트랜잭션이 롤백된다
		String ghostEmail = "ghost-%s@tenure.local".formatted(UUID.randomUUID());
		givenValidToken(ghostEmail);

		assertThatThrownBy(() -> authService.resetPassword(resetRequest(ghostEmail)))
				.isInstanceOf(CustomException.class);

		// 커밋이 일어나지 않았으니 afterCommit 콜백도 실행되지 않아야 한다 — 토큰이 살아있어야
		// 사용자가 재발송 없이 다시 시도할 수 있다
		verify(verificationStore, never()).deleteToken(TOKEN);
	}

	private void givenValidToken(String email) {
		given(verificationStore.peekToken(TOKEN))
				.willReturn(new EmailVerificationStore.VerifiedTarget(VerificationPurpose.PASSWORD_RESET, email));
	}

	private PasswordResetRequest resetRequest(String email) {
		return PasswordResetRequest.builder()
				.email(email)
				.verificationToken(TOKEN)
				.newPassword(NEW_PASSWORD)
				.build();
	}
}
