package com.tenure.domain.auth.controller;

import com.tenure.domain.auth.dto.request.EmailVerificationConfirmRequest;
import com.tenure.domain.auth.dto.request.EmailVerificationSendRequest;
import com.tenure.domain.auth.dto.response.EmailVerificationConfirmResponse;
import com.tenure.domain.auth.dto.response.EmailVerificationSendResponse;
import com.tenure.domain.auth.enums.VerificationPurpose;
import com.tenure.domain.auth.exception.AuthErrorCode;
import com.tenure.domain.auth.service.AuthService;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.global.exception.CustomException;
import com.tenure.support.ControllerSliceTestSupport;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@DisplayName("AuthController")
class AuthControllerTest extends ControllerSliceTestSupport {

	private static final String SEND_URL = "/api/v1/auth/email-verifications";
	private static final String VERIFY_URL = "/api/v1/auth/email-verifications/verify";
	private static final String EMAIL = "gildong@naver.com";

	@MockitoBean private AuthService authService;

	@Nested
	@DisplayName("POST /api/v1/auth/email-verifications")
	class SendCode {

		@Test
		@DisplayName("인증 없이 호출할 수 있고 BaseResponse 형식으로 응답한다")
		void sendCode_noAuth_returnsBaseResponse() throws Exception {
			given(authService.sendCode(any())).willReturn(
					EmailVerificationSendResponse.builder()
							.expiresAt(LocalDateTime.of(2026, 9, 27, 12, 0))
							.build());

			mvc.perform(post(SEND_URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(sendRequest(EMAIL, VerificationPurpose.SIGN_UP))))
					.andExpect(status().isOk())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.code").value("COMMON_200"))
					.andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
					.andExpect(jsonPath("$.data.expiresAt").exists());
		}

		@Test
		@DisplayName("이메일 형식이 틀리면 400 COMMON_400 을 반환한다")
		void sendCode_malformedEmail_returnsBadRequest() throws Exception {
			mvc.perform(post(SEND_URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(sendRequest("not-an-email", VerificationPurpose.SIGN_UP))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.code").value("COMMON_400"))
					.andExpect(jsonPath("$.data").isEmpty());
		}

		@Test
		@DisplayName("purpose 가 없으면 400 을 반환한다")
		void sendCode_missingPurpose_returnsBadRequest() throws Exception {
			mvc.perform(post(SEND_URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(sendRequest(EMAIL, null))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON_400"));
		}

		@Test
		@DisplayName("알 수 없는 purpose 값이면 400 을 반환한다")
		void sendCode_unknownPurpose_returnsBadRequest() throws Exception {
			// enum 에 없는 문자열은 역직렬화 단계에서 깨진다 — 500 으로 새어나가면 안 된다
			mvc.perform(post(SEND_URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"email":"%s","purpose":"WITHDRAW"}""".formatted(EMAIL)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON_400"));
		}

		@Test
		@DisplayName("본문이 깨진 JSON 이면 400 을 반환한다")
		void sendCode_brokenJson_returnsBadRequest() throws Exception {
			mvc.perform(post(SEND_URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"email\": "))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON_400"));
		}

		@Test
		@DisplayName("서비스가 던진 ErrorCode 의 상태와 코드를 그대로 내려준다")
		void sendCode_serviceThrows_propagatesErrorCode() throws Exception {
			willThrow(new CustomException(UserErrorCode.USER_409_EMAIL))
					.given(authService).sendCode(any());

			mvc.perform(post(SEND_URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(sendRequest(EMAIL, VerificationPurpose.SIGN_UP))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.code").value("USER_409_EMAIL"))
					.andExpect(jsonPath("$.message").value("이미 존재하는 이메일입니다."));
		}
	}

	@Nested
	@DisplayName("POST /api/v1/auth/email-verifications/verify")
	class ConfirmCode {

		@Test
		@DisplayName("인증번호가 맞으면 인증 토큰을 내려준다")
		void confirmCode_validCode_returnsToken() throws Exception {
			given(authService.confirmCode(any())).willReturn(
					EmailVerificationConfirmResponse.builder()
							.verificationToken("d4f1c0ab9e7b4c2f8a5d3e6b1c9f0a2d")
							.expiresAt(LocalDateTime.of(2026, 9, 27, 12, 10))
							.build());

			mvc.perform(post(VERIFY_URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(confirmRequest("123456"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.verificationToken")
							.value("d4f1c0ab9e7b4c2f8a5d3e6b1c9f0a2d"));
		}

		@Test
		@DisplayName("인증번호가 비어 있으면 400 을 반환한다")
		void confirmCode_blankCode_returnsBadRequest() throws Exception {
			mvc.perform(post(VERIFY_URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(confirmRequest("  "))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON_400"));
		}

		@Test
		@DisplayName("인증번호가 만료됐으면 410 AUTH_410_CODE 를 반환한다")
		void confirmCode_expiredCode_returnsGone() throws Exception {
			willThrow(new CustomException(AuthErrorCode.AUTH_410_CODE))
					.given(authService).confirmCode(any());

			mvc.perform(post(VERIFY_URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(confirmRequest("123456"))))
					.andExpect(status().isGone())
					.andExpect(jsonPath("$.code").value("AUTH_410_CODE"));
		}
	}

	private static EmailVerificationSendRequest sendRequest(String email, VerificationPurpose purpose) {
		return EmailVerificationSendRequest.builder()
				.email(email)
				.purpose(purpose)
				.build();
	}

	private static EmailVerificationConfirmRequest confirmRequest(String code) {
		return EmailVerificationConfirmRequest.builder()
				.email(EMAIL)
				.purpose(VerificationPurpose.SIGN_UP)
				.code(code)
				.build();
	}
}
