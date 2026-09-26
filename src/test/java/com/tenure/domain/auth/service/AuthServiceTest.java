package com.tenure.domain.auth.service;

import com.tenure.domain.auth.dto.request.EmailVerificationConfirmRequest;
import com.tenure.domain.auth.dto.request.EmailVerificationSendRequest;
import com.tenure.domain.auth.dto.response.EmailVerificationConfirmResponse;
import com.tenure.domain.auth.dto.response.EmailVerificationSendResponse;
import com.tenure.domain.auth.enums.VerificationPurpose;
import com.tenure.domain.auth.exception.AuthErrorCode;
import com.tenure.domain.auth.store.EmailVerificationStore;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import com.tenure.global.mail.EmailSender;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.tenure.domain.auth.enums.VerificationPurpose.PASSWORD_RESET;
import static com.tenure.domain.auth.enums.VerificationPurpose.SIGN_UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

	private static final String EMAIL = "gildong@naver.com";
	private static final String CODE = "123456";
	private static final String TOKEN = "d4f1c0ab9e7b4c2f8a5d3e6b1c9f0a2d";

	@Mock private EmailVerificationStore verificationStore;
	@Mock private UserRepository userRepository;
	@Mock private EmailSender emailSender;

	@InjectMocks private AuthService authService;

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
		@DisplayName("용도와 대상 이메일이 맞으면 통과한다")
		void consume_validToken_passes() {
			givenToken(SIGN_UP, EMAIL);

			authService.consumeVerificationToken(TOKEN, EMAIL, SIGN_UP);
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
			given(verificationStore.consumeToken(TOKEN)).willReturn(null);
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
			given(verificationStore.consumeToken(TOKEN)).willReturn(null);
			given(verificationStore.wasConsumed(TOKEN)).willReturn(false);

			assertThatThrownBy(() -> authService.consumeVerificationToken(TOKEN, EMAIL, SIGN_UP))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_410_VERIFICATION_TOKEN);
		}

		@Test
		@DisplayName("가입용 토큰으로 비밀번호 재설정을 하려 하면 AUTH_400_PURPOSE_MISMATCH 예외가 발생한다")
		void consume_purposeMismatch_throwsBadRequest() {
			givenToken(SIGN_UP, EMAIL);

			assertThatThrownBy(() -> authService.consumeVerificationToken(TOKEN, EMAIL, PASSWORD_RESET))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_400_PURPOSE_MISMATCH);
		}

		@Test
		@DisplayName("남의 토큰으로 다른 이메일을 인증하려 하면 AUTH_401_VERIFICATION_TOKEN 예외가 발생한다")
		void consume_emailMismatch_throwsUnauthorized() {
			givenToken(SIGN_UP, "other@naver.com");

			assertThatThrownBy(() -> authService.consumeVerificationToken(TOKEN, EMAIL, SIGN_UP))
					.isInstanceOf(CustomException.class)
					.extracting(AuthServiceTest::errorCodeOf)
					.isEqualTo(AuthErrorCode.AUTH_401_VERIFICATION_TOKEN);
		}

		private void givenToken(VerificationPurpose purpose, String email) {
			given(verificationStore.consumeToken(TOKEN))
					.willReturn(new EmailVerificationStore.VerifiedTarget(purpose, email));
		}
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
