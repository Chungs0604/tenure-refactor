package com.tenure.domain.user.service;

import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.address.repository.ShippingAddressRepository;
import com.tenure.domain.auth.enums.VerificationPurpose;
import com.tenure.domain.auth.exception.AuthErrorCode;
import com.tenure.domain.auth.service.AuthService;
import com.tenure.domain.user.dto.request.UserCreateRequest;
import com.tenure.domain.user.dto.response.AvailabilityResponse;
import com.tenure.domain.user.dto.response.UserCreateResponse;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.entity.UserAgreement;
import com.tenure.domain.user.enums.AgreementType;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.enums.UserGrade;
import com.tenure.domain.user.enums.UserStatus;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserAgreementRepository;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import com.tenure.global.security.jwt.JwtTokenProvider;
import com.tenure.global.security.refresh.RefreshTokenStore;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

	private static final Long USER_ID = 1L;
	private static final String EMAIL = "gildong@naver.com";
	private static final String NICKNAME = "Sujun";
	private static final String RAW_PASSWORD = "password123";
	private static final String HASHED_PASSWORD = "$2a$10$hashed";
	private static final String TOKEN = "d4f1c0ab9e7b4c2f8a5d3e6b1c9f0a2d";

	@Mock private UserRepository userRepository;
	@Mock private UserAgreementRepository userAgreementRepository;
	@Mock private ShippingAddressRepository shippingAddressRepository;
	@Mock private AuthService authService;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private JwtTokenProvider jwtTokenProvider;
	@Mock private RefreshTokenStore refreshTokenStore;

	@InjectMocks private UserService userService;

	@Captor private ArgumentCaptor<User> userCaptor;
	@Captor private ArgumentCaptor<UserAgreement> agreementCaptor;
	@Captor private ArgumentCaptor<ShippingAddress> addressCaptor;

	@Nested
	@DisplayName("중복 확인")
	class Availability {

		@Test
		@DisplayName("가입되지 않은 이메일이면 사용 가능이다")
		void checkEmail_notJoined_available() {
			given(userRepository.existsByEmail(EMAIL)).willReturn(false);

			AvailabilityResponse response = userService.checkEmail(EMAIL);

			assertThat(response.isAvailable()).isTrue();
		}

		@Test
		@DisplayName("이미 가입된 이메일이면 사용 불가이며 예외를 던지지 않는다")
		void checkEmail_alreadyJoined_unavailable() {
			given(userRepository.existsByEmail(EMAIL)).willReturn(true);

			// 중복 확인은 조회다. 여기서 예외를 던지면 화면이 안내 문구를 띄울 수 없다
			AvailabilityResponse response = userService.checkEmail(EMAIL);

			assertThat(response.isAvailable()).isFalse();
		}

		@Test
		@DisplayName("사용 중인 닉네임이면 사용 불가이다")
		void checkNickname_alreadyTaken_unavailable() {
			given(userRepository.existsByNickname(NICKNAME)).willReturn(true);

			assertThat(userService.checkNickname(NICKNAME).isAvailable()).isFalse();
		}

		@Test
		@DisplayName("사용되지 않은 닉네임이면 사용 가능이다")
		void checkNickname_notTaken_available() {
			given(userRepository.existsByNickname(NICKNAME)).willReturn(false);

			assertThat(userService.checkNickname(NICKNAME).isAvailable()).isTrue();
		}
	}

	@Nested
	@DisplayName("회원가입")
	class SignUp {

		@Test
		@DisplayName("정상 요청이면 계정을 만들고 토큰을 발급한다")
		void signUp_validRequest_createsUserAndIssuesTokens() {
			givenSignUpSucceeds();

			UserCreateResponse response = userService.signUp(request());

			assertThat(response.getId()).isEqualTo(USER_ID);
			assertThat(response.getAccessToken()).isEqualTo("access-token");
			assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
		}

		@Test
		@DisplayName("비밀번호는 해시해서 저장한다")
		void signUp_validRequest_storesHashedPassword() {
			givenSignUpSucceeds();

			userService.signUp(request());

			verify(userRepository).save(userCaptor.capture());
			assertThat(userCaptor.getValue().getPasswordHash())
					.isEqualTo(HASHED_PASSWORD)
					.isNotEqualTo(RAW_PASSWORD);
		}

		@Test
		@DisplayName("가입 직후 등급은 BASIC, 상태는 ACTIVE, 카운터는 0 이다")
		void signUp_validRequest_startsAtBasicGrade() {
			givenSignUpSucceeds();

			userService.signUp(request());

			verify(userRepository).save(userCaptor.capture());
			User saved = userCaptor.getValue();
			assertThat(saved.getGrade()).isEqualTo(UserGrade.BASIC);
			assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
			assertThat(saved.getFollowerCount()).isZero();
			assertThat(saved.getPostCount()).isZero();
		}

		@Test
		@DisplayName("필수 약관 3종의 동의를 모두 기록한다")
		void signUp_validRequest_recordsAllAgreements() {
			givenSignUpSucceeds();

			userService.signUp(request());

			verify(userAgreementRepository, times(3)).save(agreementCaptor.capture());
			assertThat(agreementCaptor.getAllValues())
					.extracting(UserAgreement::getAgreementType)
					.containsExactlyInAnyOrder(AgreementType.values());
		}

		@Test
		@DisplayName("첫 배송지는 기본 배송지로 등록한다")
		void signUp_validRequest_registersDefaultAddress() {
			givenSignUpSucceeds();

			userService.signUp(request());

			verify(shippingAddressRepository).save(addressCaptor.capture());
			ShippingAddress saved = addressCaptor.getValue();
			assertThat(saved.getIsDefault()).isTrue();
			assertThat(saved.getRecipientName()).isEqualTo("홍길동");
		}

		@Test
		@DisplayName("refresh token 을 저장소에 넣는다")
		void signUp_validRequest_savesRefreshToken() {
			givenSignUpSucceeds();

			userService.signUp(request());

			// Redis 에 넣지 않으면 발급한 refresh token 으로 재발급이 되지 않는다 (DEC-010)
			verify(refreshTokenStore).save(USER_ID, "token-id", Duration.ofDays(14));
		}

		@Test
		@DisplayName("인증 토큰이 유효하지 않으면 계정을 만들지 않는다")
		void signUp_invalidVerificationToken_createsNothing() {
			willThrow(new CustomException(AuthErrorCode.AUTH_410_VERIFICATION_TOKEN))
					.given(authService)
					.consumeVerificationToken(TOKEN, EMAIL, VerificationPurpose.SIGN_UP);

			assertThatThrownBy(() -> userService.signUp(request()))
					.isInstanceOf(CustomException.class)
					.extracting(UserServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_410_VERIFICATION_TOKEN);

			// 토큰 소비가 가장 먼저다 — 중복 조회조차 하지 않는다
			verifyNoInteractions(userRepository, userAgreementRepository, shippingAddressRepository);
		}

		@ParameterizedTest(name = "{0} 누락")
		@EnumSource(AgreementType.class)
		@DisplayName("필수 약관이 하나라도 빠지면 USER_400_AGREEMENT 예외가 발생한다")
		void signUp_missingAgreement_throwsBadRequest(AgreementType missing) {
			List<AgreementType> given = Arrays.stream(AgreementType.values())
					.filter(type -> type != missing)
					.toList();

			assertThatThrownBy(() -> userService.signUp(requestWithAgreements(given)))
					.isInstanceOf(CustomException.class)
					.extracting(UserServiceTest::errorCodeOf)
					.isEqualTo(UserErrorCode.USER_400_AGREEMENT);

			verifyNoInteractions(userRepository);
		}

		@Test
		@DisplayName("약관이 비어 있으면 USER_400_AGREEMENT 예외가 발생한다")
		void signUp_noAgreement_throwsBadRequest() {
			assertThatThrownBy(() -> userService.signUp(requestWithAgreements(List.of())))
					.isInstanceOf(CustomException.class)
					.extracting(UserServiceTest::errorCodeOf)
					.isEqualTo(UserErrorCode.USER_400_AGREEMENT);
		}

		@Test
		@DisplayName("이메일이 중복이면 USER_409_EMAIL 예외가 발생한다")
		void signUp_duplicateEmail_throwsConflict() {
			given(userRepository.existsByEmail(EMAIL)).willReturn(true);

			assertThatThrownBy(() -> userService.signUp(request()))
					.isInstanceOf(CustomException.class)
					.extracting(UserServiceTest::errorCodeOf)
					.isEqualTo(UserErrorCode.USER_409_EMAIL);

			verify(userRepository, times(0)).save(any());
		}

		@Test
		@DisplayName("닉네임이 중복이면 USER_409_NICKNAME 예외가 발생한다")
		void signUp_duplicateNickname_throwsConflict() {
			given(userRepository.existsByEmail(EMAIL)).willReturn(false);
			given(userRepository.existsByNickname(NICKNAME)).willReturn(true);

			assertThatThrownBy(() -> userService.signUp(request()))
					.isInstanceOf(CustomException.class)
					.extracting(UserServiceTest::errorCodeOf)
					.isEqualTo(UserErrorCode.USER_409_NICKNAME);

			verify(userRepository, times(0)).save(any());
		}

		private void givenSignUpSucceeds() {
			given(userRepository.existsByEmail(EMAIL)).willReturn(false);
			given(userRepository.existsByNickname(NICKNAME)).willReturn(false);
			given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(HASHED_PASSWORD);
			// save 는 id 가 박힌 엔티티를 돌려준다. 이후 토큰 발급이 id 에 의존한다
			given(userRepository.save(any(User.class))).willAnswer(invocation -> {
				User user = invocation.getArgument(0);
				ReflectionTestUtils.setField(user, "id", USER_ID);
				return user;
			});
			given(jwtTokenProvider.issueRefreshToken(USER_ID, false)).willReturn(
					new JwtTokenProvider.IssuedRefreshToken(
							"refresh-token", "token-id", Duration.ofDays(14)));
			given(jwtTokenProvider.issueAccessToken(USER_ID)).willReturn("access-token");
		}
	}

	private static UserCreateRequest request() {
		return requestWithAgreements(Arrays.asList(AgreementType.values()));
	}

	private static UserCreateRequest requestWithAgreements(List<AgreementType> types) {
		return UserCreateRequest.builder()
				.email(EMAIL)
				.password(RAW_PASSWORD)
				.verificationToken(TOKEN)
				.realName("홍길동")
				.phone("821012345678")
				.nickname(NICKNAME)
				.gender(Gender.MALE)
				.heightCm(170)
				.weightKg(64)
				.agreements(types.stream()
						.map(type -> UserCreateRequest.AgreementRequest.builder()
								.agreementType(type)
								.agreed(true)
								.build())
						.toList())
				.address(UserCreateRequest.AddressRequest.builder()
						.recipientName("홍길동")
						.phone("010-1234-5678")
						.address("서울특별시 동작구 상도로 369")
						.addressDetail("104동 201호")
						.build())
				.build();
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
