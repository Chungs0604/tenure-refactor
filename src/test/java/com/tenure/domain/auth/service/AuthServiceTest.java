package com.tenure.domain.auth.service;

import com.tenure.domain.auth.dto.request.EmailVerificationConfirmRequest;
import com.tenure.domain.auth.dto.request.EmailVerificationSendRequest;
import com.tenure.domain.auth.dto.request.LoginRequest;
import com.tenure.domain.auth.dto.request.PasswordResetRequest;
import com.tenure.domain.auth.dto.request.TokenReissueRequest;
import com.tenure.domain.auth.dto.response.EmailVerificationConfirmResponse;
import com.tenure.domain.auth.dto.response.EmailVerificationSendResponse;
import com.tenure.domain.auth.dto.response.LoginResponse;
import com.tenure.domain.auth.dto.response.TokenReissueResponse;
import com.tenure.domain.auth.enums.VerificationPurpose;
import com.tenure.domain.auth.exception.AuthErrorCode;
import com.tenure.domain.auth.store.EmailVerificationStore;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.enums.UserGrade;
import com.tenure.domain.user.enums.UserStatus;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import com.tenure.global.mail.EmailSender;
import com.tenure.global.security.jwt.JwtTokenProvider;
import com.tenure.global.security.refresh.RefreshTokenStore;
import com.tenure.global.storage.FileStorage;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static com.tenure.domain.auth.enums.VerificationPurpose.PASSWORD_RESET;
import static com.tenure.domain.auth.enums.VerificationPurpose.SIGN_UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

	private static final String EMAIL = "gildong@naver.com";
	private static final String CODE = "123456";
	private static final String TOKEN = "d4f1c0ab9e7b4c2f8a5d3e6b1c9f0a2d";
	private static final Long USER_ID = 1L;
	private static final String NICKNAME = "gildong";
	private static final String RAW_PASSWORD = "password123";
	private static final String HASHED_PASSWORD = "$2a$10$hashed";
	private static final String IMAGE_KEY = "user/2026/06/ab12.jpg";

	@Mock private EmailVerificationStore verificationStore;
	@Mock private UserRepository userRepository;
	@Mock private EmailSender emailSender;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private JwtTokenProvider jwtTokenProvider;
	@Mock private RefreshTokenStore refreshTokenStore;
	@Mock private FileStorage fileStorage;

	@InjectMocks private AuthService authService;

	@Nested
	@DisplayName("로그인")
	class Login {

		@Test
		@DisplayName("이메일·비밀번호가 맞으면 토큰과 사용자 정보를 내려준다")
		void login_validCredentials_issuesTokens() {
			givenUser(activeUser(IMAGE_KEY));
			givenPasswordMatches(true);
			givenTokensIssued(false);
			given(fileStorage.toUrl(IMAGE_KEY)).willReturn("https://cdn.tenure.app/" + IMAGE_KEY);

			LoginResponse response = authService.login(loginRequest(false));

			assertThat(response.getAccessToken()).isEqualTo("access-token");
			assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
			assertThat(response.getUser().getId()).isEqualTo(USER_ID);
			assertThat(response.getUser().getNickname()).isEqualTo(NICKNAME);
			assertThat(response.getUser().getProfileImageUrl())
					.isEqualTo("https://cdn.tenure.app/" + IMAGE_KEY);
			assertThat(response.getUser().getGrade()).isEqualTo(UserGrade.BASIC);

			// 발급한 tokenId 가 저장되지 않으면 재발급이 곧바로 401 이 된다
			verify(refreshTokenStore).save(USER_ID, "token-id", Duration.ofDays(14));
		}

		@Test
		@DisplayName("프로필 사진이 없으면 URL 없이 내려준다")
		void login_noProfileImage_returnsNullUrl() {
			givenUser(activeUser(null));
			givenPasswordMatches(true);
			givenTokensIssued(false);

			LoginResponse response = authService.login(loginRequest(false));

			assertThat(response.getUser().getProfileImageUrl()).isNull();
			// 키가 없는데 저장소를 치면 base-url 만 붙은 깨진 URL 이 나간다
			verifyNoInteractions(fileStorage);
		}

		@Test
		@DisplayName("로그인 상태 유지를 켜면 긴 수명으로 리프레시 토큰을 발급한다")
		void login_keepLoggedIn_issuesLongLivedRefreshToken() {
			givenUser(activeUser(null));
			givenPasswordMatches(true);
			givenTokensIssued(true);

			authService.login(loginRequest(true));

			verify(jwtTokenProvider).issueRefreshToken(USER_ID, true);
		}

		@Test
		@DisplayName("가입되지 않은 이메일이면 AUTH_404_EMAIL 예외가 발생한다")
		void login_unknownEmail_throwsNotFound() {
			given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

			assertThatThrownBy(() -> authService.login(loginRequest(false)))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_404_EMAIL);

			verifyNoInteractions(jwtTokenProvider, refreshTokenStore);
		}

		@Test
		@DisplayName("비밀번호가 틀리면 AUTH_401_CREDENTIAL 예외가 발생한다")
		void login_wrongPassword_throwsUnauthorized() {
			givenUser(activeUser(null));
			givenPasswordMatches(false);

			assertThatThrownBy(() -> authService.login(loginRequest(false)))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_401_CREDENTIAL);

			verifyNoInteractions(jwtTokenProvider, refreshTokenStore);
		}

		@Test
		@DisplayName("탈퇴한 계정이면 AUTH_404_EMAIL 예외가 발생한다")
		void login_withdrawnAccount_throwsNotFound() {
			// 익명화(DEC-004) 전 상태. 비밀번호를 보기 전에 끊어야 한다
			User withdrawn = activeUser(null);
			ReflectionTestUtils.setField(withdrawn, "status", UserStatus.WITHDRAWN);
			givenUser(withdrawn);

			assertThatThrownBy(() -> authService.login(loginRequest(false)))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_404_EMAIL);

			verifyNoInteractions(passwordEncoder, jwtTokenProvider, refreshTokenStore);
		}

		private void givenUser(User user) {
			given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
		}

		private void givenPasswordMatches(boolean matches) {
			given(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).willReturn(matches);
		}

		private void givenTokensIssued(boolean keepLoggedIn) {
			given(jwtTokenProvider.issueRefreshToken(USER_ID, keepLoggedIn)).willReturn(
					new JwtTokenProvider.IssuedRefreshToken(
							"refresh-token", "token-id", Duration.ofDays(14)));
			given(jwtTokenProvider.issueAccessToken(USER_ID)).willReturn("access-token");
		}
	}

	@Nested
	@DisplayName("로그아웃")
	class Logout {

		@Test
		@DisplayName("이 사용자의 리프레시 토큰을 전부 무효화한다")
		void logout_authenticatedUser_revokesAllRefreshTokens() {
			authService.logout(USER_ID);

			// 기기 단위로 끊을 수 없다 — access token 에 tokenId 클레임이 없다
			verify(refreshTokenStore).revokeAll(USER_ID);
		}
	}

	@Nested
	@DisplayName("액세스 토큰 재발급")
	class Reissue {

		private static final String REFRESH_TOKEN = "refresh-token";
		private static final String TOKEN_ID = "token-id";

		@Test
		@DisplayName("살아 있는 리프레시 토큰이면 새 액세스 토큰을 발급한다")
		void reissue_aliveRefreshToken_issuesAccessToken() {
			givenParsedRefreshToken(TOKEN_ID);
			given(refreshTokenStore.isAlive(USER_ID, TOKEN_ID)).willReturn(true);
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(activeUser(null)));
			given(jwtTokenProvider.issueAccessToken(USER_ID)).willReturn("new-access-token");

			TokenReissueResponse response = authService.reissue(reissueRequest());

			assertThat(response.getAccessToken()).isEqualTo("new-access-token");
		}

		@Test
		@DisplayName("탈퇴한 계정이면 AUTH_401_TOKEN 예외가 발생한다")
		void reissue_withdrawnUser_throwsUnauthorized() {
			// withdraw() 의 Redis 반영(afterCommit)이 일부만 실행돼도 isAlive() 만으로는 못 거른다 —
			// DB 를 직접 봐서 막아야 한다
			givenParsedRefreshToken(TOKEN_ID);
			given(refreshTokenStore.isAlive(USER_ID, TOKEN_ID)).willReturn(true);
			User withdrawn = activeUser(null);
			ReflectionTestUtils.setField(withdrawn, "status", UserStatus.WITHDRAWN);
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(withdrawn));

			assertThatThrownBy(() -> authService.reissue(reissueRequest()))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_401_TOKEN);

			verify(jwtTokenProvider, never()).issueAccessToken(any());
		}

		@Test
		@DisplayName("계정이 존재하지 않으면 AUTH_401_TOKEN 예외가 발생한다")
		void reissue_userGone_throwsUnauthorized() {
			givenParsedRefreshToken(TOKEN_ID);
			given(refreshTokenStore.isAlive(USER_ID, TOKEN_ID)).willReturn(true);
			given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> authService.reissue(reissueRequest()))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_401_TOKEN);

			verify(jwtTokenProvider, never()).issueAccessToken(any());
		}

		@Test
		@DisplayName("서명이 깨졌거나 만료됐으면 AUTH_401_TOKEN 예외가 발생한다")
		void reissue_invalidToken_throwsUnauthorized() {
			given(jwtTokenProvider.parseOrNull(REFRESH_TOKEN)).willReturn(null);

			assertThatThrownBy(() -> authService.reissue(reissueRequest()))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_401_TOKEN);

			verifyNoInteractions(refreshTokenStore);
		}

		@Test
		@DisplayName("액세스 토큰을 넣으면 AUTH_401_TOKEN 예외가 발생한다")
		void reissue_accessToken_throwsUnauthorized() {
			// tid 클레임이 없는 것이 access token 이다. 이걸로 재발급이 되면 refresh 무효화가 무력해진다
			givenParsedRefreshToken(null);

			assertThatThrownBy(() -> authService.reissue(reissueRequest()))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_401_TOKEN);

			verifyNoInteractions(refreshTokenStore);
		}

		@Test
		@DisplayName("로그아웃으로 무효화된 토큰이면 AUTH_401_TOKEN 예외가 발생한다")
		void reissue_revokedToken_throwsUnauthorized() {
			givenParsedRefreshToken(TOKEN_ID);
			given(refreshTokenStore.isAlive(USER_ID, TOKEN_ID)).willReturn(false);

			assertThatThrownBy(() -> authService.reissue(reissueRequest()))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_401_TOKEN);

			verify(jwtTokenProvider, never()).issueAccessToken(any());
		}

		private void givenParsedRefreshToken(String tokenId) {
			Claims claims = mock(Claims.class);
			given(jwtTokenProvider.parseOrNull(REFRESH_TOKEN)).willReturn(claims);
			given(jwtTokenProvider.getTokenId(claims)).willReturn(tokenId);
			if (tokenId != null) {
				given(jwtTokenProvider.getUserId(claims)).willReturn(USER_ID);
			}
		}

		private TokenReissueRequest reissueRequest() {
			return TokenReissueRequest.builder().refreshToken(REFRESH_TOKEN).build();
		}
	}

	@Nested
	@DisplayName("인증번호 발송")
	class SendCode {

		@Test
		@DisplayName("가입용이고 미가입 이메일이면 인증번호를 발송한다")
		void sendCode_signUpWithNewEmail_sendsMail() {
			given(userRepository.existsByEmail(EMAIL)).willReturn(false);
			given(verificationStore.issueCode(EMAIL, SIGN_UP)).willReturn(CODE);

			LocalDateTime before = LocalDateTime.now();
			EmailVerificationSendResponse response = authService.sendCode(sendRequest(SIGN_UP));

			verify(emailSender).sendVerificationCode(EMAIL, CODE);
			// 만료 시각은 호출 시점 + CODE_TTL 이다. 호출 전후를 감싸 시계에 의존하지 않게 본다
			assertThat(response.getExpiresAt())
					.isAfterOrEqualTo(before.plus(EmailVerificationStore.CODE_TTL))
					.isBeforeOrEqualTo(LocalDateTime.now().plus(EmailVerificationStore.CODE_TTL));
			// 쿨다운·일일 한도 집계가 안 되면 재전송 제한이 동작하지 않는다
			verify(verificationStore).markSent(EMAIL, SIGN_UP);
		}

		@Test
		@DisplayName("재전송 쿨다운 중이면 AUTH_429_CODE_RESEND_COOLDOWN 예외가 발생하고 메일을 보내지 않는다")
		void sendCode_inCooldown_throwsTooManyRequests() {
			given(verificationStore.isInSendCooldown(EMAIL, SIGN_UP)).willReturn(true);

			assertThatThrownBy(() -> authService.sendCode(sendRequest(SIGN_UP)))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_429_CODE_RESEND_COOLDOWN);

			// 쿨다운에서 막히면 가입 여부 조회·발송 전부 필요 없다
			verifyNoInteractions(userRepository, emailSender);
			verify(verificationStore, never()).issueCode(anyString(), any());
		}

		@Test
		@DisplayName("일일 발송 한도를 넘었으면 AUTH_429_CODE_DAILY_LIMIT 예외가 발생하고 메일을 보내지 않는다")
		void sendCode_exceedsDailyLimit_throwsTooManyRequests() {
			given(verificationStore.isInSendCooldown(EMAIL, SIGN_UP)).willReturn(false);
			given(verificationStore.exceedsDailySendLimit(EMAIL, SIGN_UP)).willReturn(true);

			assertThatThrownBy(() -> authService.sendCode(sendRequest(SIGN_UP)))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_429_CODE_DAILY_LIMIT);

			verifyNoInteractions(userRepository, emailSender);
			verify(verificationStore, never()).issueCode(anyString(), any());
		}

		@Test
		@DisplayName("비밀번호 찾기이고 가입된 이메일이면 인증번호를 발송한다")
		void sendCode_passwordResetWithJoinedEmail_sendsMail() {
			given(userRepository.existsByEmail(EMAIL)).willReturn(true);
			given(verificationStore.issueCode(EMAIL, PASSWORD_RESET)).willReturn(CODE);

			authService.sendCode(sendRequest(PASSWORD_RESET));

			verify(emailSender).sendVerificationCode(EMAIL, CODE);
		}

		@Test
		@DisplayName("가입용인데 이미 가입된 이메일이면 USER_409_EMAIL 예외가 발생한다")
		void sendCode_signUpWithJoinedEmail_throwsConflict() {
			given(userRepository.existsByEmail(EMAIL)).willReturn(true);

			assertThatThrownBy(() -> authService.sendCode(sendRequest(SIGN_UP)))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(UserErrorCode.USER_409_EMAIL);
		}

		@Test
		@DisplayName("비밀번호 찾기인데 가입 이력이 없으면 AUTH_404_EMAIL 예외가 발생한다")
		void sendCode_passwordResetWithUnknownEmail_throwsNotFound() {
			given(userRepository.existsByEmail(EMAIL)).willReturn(false);

			assertThatThrownBy(() -> authService.sendCode(sendRequest(PASSWORD_RESET)))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_404_EMAIL);
		}

		@Test
		@DisplayName("발송 조건을 통과하지 못하면 인증번호를 발급하지도, 메일을 보내지도 않는다")
		void sendCode_rejected_issuesNothing() {
			given(userRepository.existsByEmail(EMAIL)).willReturn(true);

			assertThatThrownBy(() -> authService.sendCode(sendRequest(SIGN_UP)))
					.isInstanceOf(CustomException.class);

			// 메일이 나가면 존재하지 않는 계정에 인증번호를 뿌리는 것이 된다
			verifyNoInteractions(emailSender);
			verify(verificationStore, never()).issueCode(anyString(), any());
		}
	}

	@Nested
	@DisplayName("인증번호 확인")
	class ConfirmCode {

		@Test
		@DisplayName("인증번호가 일치하면 1회용 토큰을 발급한다")
		void confirmCode_matchingCode_issuesToken() {
			given(verificationStore.findCode(EMAIL, SIGN_UP)).willReturn(CODE);
			given(verificationStore.issueToken(EMAIL, SIGN_UP)).willReturn(TOKEN);

			LocalDateTime before = LocalDateTime.now();
			EmailVerificationConfirmResponse response = authService.confirmCode(confirmRequest(CODE));

			assertThat(response.getVerificationToken()).isEqualTo(TOKEN);
			assertThat(response.getExpiresAt())
					.isAfterOrEqualTo(before.plus(EmailVerificationStore.TOKEN_TTL))
					.isBeforeOrEqualTo(LocalDateTime.now().plus(EmailVerificationStore.TOKEN_TTL));
		}

		@Test
		@DisplayName("인증번호가 틀려도 한도 전이면 시도 횟수만 늘리고 코드는 지우지 않는다")
		void confirmCode_wrongCode_underLimit_onlyCountsAttempt() {
			given(verificationStore.findCode(EMAIL, SIGN_UP)).willReturn(CODE);
			given(verificationStore.incrementConfirmAttempts(EMAIL, SIGN_UP)).willReturn(3L);

			assertThatThrownBy(() -> authService.confirmCode(confirmRequest("999999")))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_400_CODE);

			// 아직 브루트포스 한도(5회)를 넘지 않았으니 코드는 그대로 살아있어야 한다
			verify(verificationStore, never()).deleteCode(anyString(), any());
		}

		@Test
		@DisplayName("인증번호 대조 실패가 한도를 넘으면 코드를 지우고 AUTH_410_CODE 예외가 발생한다")
		void confirmCode_wrongCode_overLimit_invalidatesCode() {
			given(verificationStore.findCode(EMAIL, SIGN_UP)).willReturn(CODE);
			given(verificationStore.incrementConfirmAttempts(EMAIL, SIGN_UP))
					.willReturn((long) EmailVerificationStore.MAX_CONFIRM_ATTEMPTS);

			assertThatThrownBy(() -> authService.confirmCode(confirmRequest("999999")))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_410_CODE);

			// 한도를 넘으면 코드를 무효화해 무차별 대입을 끊는다 — 재시도는 재발송부터
			verify(verificationStore).deleteCode(EMAIL, SIGN_UP);
			verify(verificationStore).clearConfirmAttempts(EMAIL, SIGN_UP);
		}

		@Test
		@DisplayName("인증번호가 만료됐으면 AUTH_410_CODE 예외가 발생한다")
		void confirmCode_expiredCode_throwsGone() {
			given(verificationStore.findCode(EMAIL, SIGN_UP)).willReturn(null);

			assertThatThrownBy(() -> authService.confirmCode(confirmRequest(CODE)))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_410_CODE);
		}

		@Test
		@DisplayName("인증번호가 틀리면 AUTH_400_CODE 예외가 발생한다")
		void confirmCode_wrongCode_throwsBadRequest() {
			given(verificationStore.findCode(EMAIL, SIGN_UP)).willReturn(CODE);

			assertThatThrownBy(() -> authService.confirmCode(confirmRequest("999999")))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_400_CODE);
		}

		@Test
		@DisplayName("인증번호가 틀리면 토큰을 발급하지 않는다")
		void confirmCode_wrongCode_issuesNoToken() {
			given(verificationStore.findCode(EMAIL, SIGN_UP)).willReturn(CODE);

			assertThatThrownBy(() -> authService.confirmCode(confirmRequest("999999")))
					.isInstanceOf(CustomException.class);

			verify(verificationStore, never()).issueToken(anyString(), any());
		}
	}

	@Nested
	@DisplayName("인증 토큰 소비")
	class ConsumeVerificationToken {

		@Test
		@DisplayName("용도와 대상 이메일이 맞으면 통과하고 토큰을 지운다")
		void consume_validToken_passes() {
			givenToken(SIGN_UP, EMAIL);

			authService.consumeVerificationToken(TOKEN, EMAIL, SIGN_UP);

			// 트랜잭션 동기화가 없는 이 테스트에서는 afterCommit 콜백이 즉시 실행된다 —
			// 실제 트랜잭션 안에서는 커밋 뒤로 미뤄진다(AuthServiceVerificationTokenCommitTimingTest 참고)
			verify(verificationStore).deleteToken(TOKEN);
		}

		@Test
		@DisplayName("대상 이메일의 대소문자가 달라도 통과한다")
		void consume_emailCaseDiffers_passes() {
			// DB collation(utf8mb4_0900_ai_ci)이 대소문자를 무시하므로 인증도 같은 기준이어야 한다.
			// 여기서 막으면 Gildong@naver.com 으로 인증받고 가입에서 걸리는 상태가 된다
			givenToken(SIGN_UP, "Gildong@Naver.com");

			authService.consumeVerificationToken(TOKEN, EMAIL, SIGN_UP);
		}

		@Test
		@DisplayName("이미 사용된 토큰이면 AUTH_409_TOKEN_CONSUMED 예외가 발생한다")
		void consume_alreadyUsedToken_throwsConflict() {
			given(verificationStore.peekToken(TOKEN)).willReturn(null);
			given(verificationStore.wasConsumed(TOKEN)).willReturn(true);

			assertThatThrownBy(() -> authService.consumeVerificationToken(TOKEN, EMAIL, SIGN_UP))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_409_TOKEN_CONSUMED);
		}

		@Test
		@DisplayName("만료된 토큰이면 AUTH_410_VERIFICATION_TOKEN 예외가 발생한다")
		void consume_expiredToken_throwsGone() {
			// 소비 흔적이 없다 = 애초에 발급된 적이 없거나 TTL 이 지난 것
			given(verificationStore.peekToken(TOKEN)).willReturn(null);
			given(verificationStore.wasConsumed(TOKEN)).willReturn(false);

			assertThatThrownBy(() -> authService.consumeVerificationToken(TOKEN, EMAIL, SIGN_UP))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_410_VERIFICATION_TOKEN);
		}

		@Test
		@DisplayName("가입용 토큰으로 비밀번호 재설정을 하려 하면 AUTH_400_PURPOSE_MISMATCH 예외가 발생하고 토큰을 지우지 않는다")
		void consume_purposeMismatch_throwsBadRequest() {
			givenToken(SIGN_UP, EMAIL);

			assertThatThrownBy(() -> authService.consumeVerificationToken(TOKEN, EMAIL, PASSWORD_RESET))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_400_PURPOSE_MISMATCH);

			// 검증에 실패하면 토큰을 살려둬야 한다 — 올바른 용도로 재시도할 수 있게
			verify(verificationStore, never()).deleteToken(anyString());
		}

		@Test
		@DisplayName("남의 토큰으로 다른 이메일을 인증하려 하면 AUTH_401_VERIFICATION_TOKEN 예외가 발생하고 토큰을 지우지 않는다")
		void consume_emailMismatch_throwsUnauthorized() {
			givenToken(SIGN_UP, "other@naver.com");

			assertThatThrownBy(() -> authService.consumeVerificationToken(TOKEN, EMAIL, SIGN_UP))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_401_VERIFICATION_TOKEN);

			verify(verificationStore, never()).deleteToken(anyString());
		}

		private void givenToken(VerificationPurpose purpose, String email) {
			given(verificationStore.peekToken(TOKEN))
					.willReturn(new EmailVerificationStore.VerifiedTarget(purpose, email));
		}
	}

	@Nested
	@DisplayName("비밀번호 재설정")
	class ResetPassword {

		private static final String NEW_PASSWORD = "newPassword123";

		@Test
		@DisplayName("비밀번호 찾기 토큰이 맞으면 해시로 바꿔 저장한다")
		void resetPassword_validToken_updatesPasswordHash() {
			givenToken(PASSWORD_RESET);
			User user = activeUser(null);
			given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
			given(passwordEncoder.encode(NEW_PASSWORD)).willReturn("$2a$10$newHash");

			authService.resetPassword(resetRequest(NEW_PASSWORD));

			// 평문이 들어가면 다음 로그인이 전부 실패한다
			assertThat(user.getPasswordHash()).isEqualTo("$2a$10$newHash");
			// 트랜잭션 동기화가 없는 이 테스트에서는 afterCommit 콜백이 즉시 실행된다 —
			// 실제 트랜잭션에서 성공·실패에 따라 미뤄지는지는 AuthServiceVerificationTokenCommitTimingTest 가 본다
			verify(verificationStore).deleteToken(TOKEN);
		}

		@Test
		@DisplayName("재설정하면 기존 리프레시 토큰을 전부 무효화한다")
		void resetPassword_validToken_revokesRefreshTokens() {
			givenToken(PASSWORD_RESET);
			given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(activeUser(null)));
			given(passwordEncoder.encode(NEW_PASSWORD)).willReturn("$2a$10$newHash");

			authService.resetPassword(resetRequest(NEW_PASSWORD));

			// 세션을 남겨두면 비밀번호를 바꾼 이유(탈취)가 해소되지 않는다
			verify(refreshTokenStore).revokeAll(USER_ID);
		}

		@Test
		@DisplayName("가입용 토큰이면 AUTH_400_PURPOSE_MISMATCH 예외가 발생하고 비밀번호를 바꾸지 않는다")
		void resetPassword_signUpToken_throwsBadRequest() {
			givenToken(SIGN_UP);

			assertThatThrownBy(() -> authService.resetPassword(resetRequest(NEW_PASSWORD)))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_400_PURPOSE_MISMATCH);

			// 토큰이 유효하지 않으면 계정을 조회하지도 않는다
			verifyNoInteractions(userRepository, passwordEncoder);
		}

		@Test
		@DisplayName("이미 사용된 토큰이면 AUTH_409_TOKEN_CONSUMED 예외가 발생한다")
		void resetPassword_consumedToken_throwsConflict() {
			given(verificationStore.peekToken(TOKEN)).willReturn(null);
			given(verificationStore.wasConsumed(TOKEN)).willReturn(true);

			assertThatThrownBy(() -> authService.resetPassword(resetRequest(NEW_PASSWORD)))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_409_TOKEN_CONSUMED);

			verifyNoInteractions(userRepository, passwordEncoder);
		}

		@Test
		@DisplayName("인증은 통과했지만 계정이 없으면 USER_404 예외가 발생한다")
		void resetPassword_userGone_throwsNotFound() {
			givenToken(PASSWORD_RESET);
			given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

			assertThatThrownBy(() -> authService.resetPassword(resetRequest(NEW_PASSWORD)))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(UserErrorCode.USER_404);
		}

		private void givenToken(VerificationPurpose purpose) {
			given(verificationStore.peekToken(TOKEN))
					.willReturn(new EmailVerificationStore.VerifiedTarget(purpose, EMAIL));
		}

		private PasswordResetRequest resetRequest(String newPassword) {
			return PasswordResetRequest.builder()
					.email(EMAIL)
					.verificationToken(TOKEN)
					.newPassword(newPassword)
					.build();
		}
	}

	private static LoginRequest loginRequest(boolean keepLoggedIn) {
		return LoginRequest.builder()
				.email(EMAIL)
				.password(RAW_PASSWORD)
				.keepLoggedIn(keepLoggedIn)
				.build();
	}

	/** id 가 박힌 활성 계정. 엔티티 팩토리는 id 를 만들지 않으므로 저장 이후 상태를 재현한다. */
	private static User activeUser(String profileImageKey) {
		User user = User.signUp(EMAIL, HASHED_PASSWORD, "홍길동", "821012345678",
				NICKNAME, profileImageKey, Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", USER_ID);
		return user;
	}

	private static EmailVerificationSendRequest sendRequest(VerificationPurpose purpose) {
		return EmailVerificationSendRequest.builder()
				.email(EMAIL)
				.purpose(purpose)
				.build();
	}

	private static EmailVerificationConfirmRequest confirmRequest(String code) {
		return EmailVerificationConfirmRequest.builder()
				.email(EMAIL)
				.purpose(SIGN_UP)
				.code(code)
				.build();
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
