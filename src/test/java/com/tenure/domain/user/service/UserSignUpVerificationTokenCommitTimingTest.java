package com.tenure.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.tenure.domain.auth.enums.VerificationPurpose;
import com.tenure.domain.auth.store.EmailVerificationStore;
import com.tenure.domain.user.dto.request.UserCreateRequest;
import com.tenure.domain.user.enums.AgreementType;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.security.refresh.RefreshTokenStore;
import com.tenure.support.IntegrationTestSupport;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code signUp()} 이 인증 토큰을 DB 커밋 전까지 지우지 않는지 확인한다(이슈 e).
 *
 * <p>검증 패턴은 {@code UserWithdrawCommitTimingTest} 와 같다 — {@code @Transactional} 테스트
 * 메서드는 끝나면 Spring Test 가 항상 롤백하므로, 메서드 안에서는 커밋이 일어나지 않는다. 그 시점에
 * {@code EmailVerificationStore.deleteToken} 이 전혀 불리지 않아야 토큰 소비가 커밋 뒤로
 * 미뤄졌다는 뜻이다 — 이전에는 {@code GETDEL} 로 즉시 지웠기 때문에, 이 가입이 (예: 동시 닉네임
 * 충돌로) 나중에 롤백돼도 토큰은 이미 사라진 뒤였다.
 *
 * <p>{@link EmailVerificationStore}·{@link RefreshTokenStore} 를 모킹한다 — 이 프로젝트에
 * 실제 Redis 에 연결하는 테스트 패턴이 없다({@code HolidayStoreTest} 참고).
 */
@DisplayName("회원가입 — 인증 토큰 소비 시점")
class UserSignUpVerificationTokenCommitTimingTest extends IntegrationTestSupport {

	private static final String TOKEN = "d4f1c0ab9e7b4c2f8a5d3e6b1c9f0a2d";

	@Autowired private UserService userService;
	@Autowired private UserRepository userRepository;

	@MockitoBean private EmailVerificationStore verificationStore;
	@MockitoBean private RefreshTokenStore refreshTokenStore;

	@Test
	@Transactional
	@DisplayName("트랜잭션이 아직 커밋되지 않은 시점에는 인증 토큰을 지우지 않는다")
	void signUp_beforeCommit_doesNotDeleteToken() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		String email = "signup-commit-timing-%s@tenure.local".formatted(tag);
		given(verificationStore.peekToken(TOKEN))
				.willReturn(new EmailVerificationStore.VerifiedTarget(VerificationPurpose.SIGN_UP, email));

		userService.signUp(request(email, "stiming-%s".formatted(tag)));

		// 계정 생성은 같은 트랜잭션 안이라 바로 보인다
		assertThat(userRepository.existsByEmail(email)).isTrue();

		// 커밋은 이 테스트 메서드가 끝난 뒤 Spring Test 가 대신 롤백하므로 아직 일어나지 않았다 —
		// 가입 뒤 닉네임 유니크 충돌 등으로 실제로 롤백되는 경우도 같은 메커니즘(커밋 전 미실행)이라
		// 토큰이 살아남는다
		verify(verificationStore, never()).deleteToken(TOKEN);
	}

	private static UserCreateRequest request(String email, String nickname) {
		return UserCreateRequest.builder()
				.email(email)
				.password("password123")
				.verificationToken(TOKEN)
				.realName("홍길동")
				.phone("821012345678")
				.nickname(nickname)
				.gender(Gender.MALE)
				.heightCm(170)
				.weightKg(64)
				.agreements(Arrays.stream(AgreementType.values())
						.map(type -> UserCreateRequest.AgreementRequest.builder()
								.agreementType(type)
								.agreed(true)
								.build())
						.toList())
				.address(UserCreateRequest.AddressRequest.builder()
						.recipientName("홍길동")
						.phone("010-1234-5678")
						.zipcode("07027")
						.address("서울특별시 동작구 상도로 369")
						.addressDetail("104동 201호")
						.build())
				.build();
	}
}
