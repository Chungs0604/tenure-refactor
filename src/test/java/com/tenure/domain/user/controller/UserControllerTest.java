package com.tenure.domain.user.controller;

import com.tenure.domain.auth.exception.AuthErrorCode;
import com.tenure.domain.user.dto.request.UserCreateRequest;
import com.tenure.domain.user.dto.request.UserUpdateRequest;
import com.tenure.domain.user.dto.response.AccountResponse;
import com.tenure.domain.user.dto.response.AvailabilityResponse;
import com.tenure.domain.user.dto.response.MyProfileResponse;
import com.tenure.domain.user.dto.response.UserCreateResponse;
import com.tenure.domain.user.dto.response.UserProfileResponse;
import com.tenure.domain.user.dto.response.UserUpdateResponse;
import com.tenure.domain.user.enums.AgreementType;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.enums.UserGrade;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.service.UserService;
import com.tenure.global.exception.CustomException;
import com.tenure.global.security.AuthUser;
import com.tenure.support.ControllerSliceTestSupport;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@DisplayName("UserController")
class UserControllerTest extends ControllerSliceTestSupport {

	private static final String USERS_URL = "/api/v1/users";
	private static final String EMAIL = "gildong@naver.com";
	private static final String NICKNAME = "Sujun";
	private static final Long USER_ID = 1L;
	private static final Long OTHER_ID = 2L;
	private static final String IMAGE_URL = "https://cdn.tenure.app/user/2026/09/ab12.jpg";

	@MockitoBean private UserService userService;

	@Nested
	@DisplayName("GET /api/v1/users/check-email · check-nickname")
	class Availability {

		@Test
		@DisplayName("인증 없이 호출할 수 있고 사용 가능 여부를 내려준다")
		void checkEmail_noAuth_returnsAvailability() throws Exception {
			given(userService.checkEmail(EMAIL)).willReturn(available(true));

			mvc.perform(get(USERS_URL + "/check-email").param("email", EMAIL))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data.available").value(true));
		}

		@Test
		@DisplayName("중복이어도 200 으로 내려준다")
		void checkEmail_duplicated_returnsOkWithFalse() throws Exception {
			given(userService.checkEmail(EMAIL)).willReturn(available(false));

			// 중복은 오류가 아니다. 400 으로 내리면 화면이 안내 문구 대신 에러를 띄운다
			mvc.perform(get(USERS_URL + "/check-email").param("email", EMAIL))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.available").value(false));
		}

		@Test
		@DisplayName("이메일 형식이 틀리면 400 을 반환하고 서비스를 호출하지 않는다")
		void checkEmail_malformed_returnsBadRequest() throws Exception {
			mvc.perform(get(USERS_URL + "/check-email").param("email", "not-an-email"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON_400"));

			verifyNoInteractions(userService);
		}

		@Test
		@DisplayName("파라미터가 없으면 400 을 반환한다")
		void checkEmail_missingParam_returnsBadRequest() throws Exception {
			mvc.perform(get(USERS_URL + "/check-email"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON_400"));
		}

		@Test
		@DisplayName("닉네임이 공백이면 400 을 반환한다")
		void checkNickname_blank_returnsBadRequest() throws Exception {
			mvc.perform(get(USERS_URL + "/check-nickname").param("nickname", " "))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON_400"));
		}

		@Test
		@DisplayName("사용 가능한 닉네임이면 200 을 반환한다")
		void checkNickname_available_returnsOk() throws Exception {
			given(userService.checkNickname(NICKNAME)).willReturn(available(true));

			mvc.perform(get(USERS_URL + "/check-nickname").param("nickname", NICKNAME))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.available").value(true));
		}
	}

	@Nested
	@DisplayName("POST /api/v1/users")
	class SignUp {

		@Test
		@DisplayName("인증 없이 호출할 수 있고 토큰을 내려준다")
		void signUp_noAuth_returnsTokens() throws Exception {
			given(userService.signUp(any())).willReturn(
					UserCreateResponse.builder()
							.id(1L)
							.accessToken("access-token")
							.refreshToken("refresh-token")
							.build());

			mvc.perform(post(USERS_URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(request(it -> { }))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data.id").value(1))
					.andExpect(jsonPath("$.data.accessToken").value("access-token"))
					.andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
		}

		@Test
		@DisplayName("비밀번호가 8자 미만이면 400 을 반환한다")
		void signUp_shortPassword_returnsBadRequest() throws Exception {
			// 화면 문구 '(8자 이상)' 이 근거다. Service 까지 내려가지 않고 DTO 에서 걸린다
			expectBadRequest(request(it -> it.password("1234567")));
		}

		@Test
		@DisplayName("인증 토큰이 없으면 400 을 반환한다")
		void signUp_missingVerificationToken_returnsBadRequest() throws Exception {
			expectBadRequest(request(it -> it.verificationToken(null)));
		}

		@Test
		@DisplayName("닉네임이 비어 있으면 400 을 반환한다")
		void signUp_blankNickname_returnsBadRequest() throws Exception {
			expectBadRequest(request(it -> it.nickname("  ")));
		}

		@Test
		@DisplayName("키가 허용 범위를 넘으면 400 을 반환한다")
		void signUp_heightOutOfRange_returnsBadRequest() throws Exception {
			// 검색 필터(SCR-047)의 0~200cm 와 같은 범위다. 넘으면 필터로 찾을 수 없는 프로필이 된다
			expectBadRequest(request(it -> it.heightCm(201)));
		}

		@Test
		@DisplayName("몸무게가 허용 범위를 넘으면 400 을 반환한다")
		void signUp_weightOutOfRange_returnsBadRequest() throws Exception {
			expectBadRequest(request(it -> it.weightKg(151)));
		}

		@Test
		@DisplayName("약관에 동의하지 않았으면 400 을 반환한다")
		void signUp_agreementNotAccepted_returnsBadRequest() throws Exception {
			// @AssertTrue 가 잡는다. false 를 담아 보내는 경로를 DTO 에서 막는다
			expectBadRequest(request(it -> it.agreements(
					Arrays.stream(AgreementType.values())
							.map(type -> agreement(type, false))
							.toList())));
		}

		@Test
		@DisplayName("배송지가 없으면 400 을 반환한다")
		void signUp_missingAddress_returnsBadRequest() throws Exception {
			expectBadRequest(request(it -> it.address(null)));
		}

		@Test
		@DisplayName("배송지의 필수 항목이 비어 있으면 400 을 반환한다")
		void signUp_blankAddressField_returnsBadRequest() throws Exception {
			// 중첩 DTO 는 @Valid 가 전파되어야 걸린다 — 빠뜨리면 조용히 통과한다
			expectBadRequest(request(it -> it.address(
					UserCreateRequest.AddressRequest.builder()
							.recipientName("홍길동")
							.phone("010-1234-5678")
							.address("  ")
							.build())));
		}

		@Test
		@DisplayName("필수 약관이 누락되면 서비스의 USER_400_AGREEMENT 를 그대로 내려준다")
		void signUp_missingAgreement_returnsServiceErrorCode() throws Exception {
			willThrow(new CustomException(UserErrorCode.USER_400_AGREEMENT))
					.given(userService).signUp(any());

			mvc.perform(post(USERS_URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(request(it -> it.agreements(
									List.of(agreement(AgreementType.SERVICE_TERMS, true)))))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("USER_400_AGREEMENT"));
		}

		@Test
		@DisplayName("이메일이 중복이면 409 USER_409_EMAIL 을 반환한다")
		void signUp_duplicateEmail_returnsConflict() throws Exception {
			willThrow(new CustomException(UserErrorCode.USER_409_EMAIL))
					.given(userService).signUp(any());

			expectError(request(it -> { }), 409, "USER_409_EMAIL");
		}

		@Test
		@DisplayName("인증 토큰이 만료됐으면 410 AUTH_410_VERIFICATION_TOKEN 을 반환한다")
		void signUp_expiredVerificationToken_returnsGone() throws Exception {
			willThrow(new CustomException(AuthErrorCode.AUTH_410_VERIFICATION_TOKEN))
					.given(userService).signUp(any());

			expectError(request(it -> { }), 410, "AUTH_410_VERIFICATION_TOKEN");
		}

		private void expectBadRequest(UserCreateRequest request) throws Exception {
			mvc.perform(post(USERS_URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON_400"));

			// DTO 에서 걸러야 한다. Service 까지 내려가면 검증 위치가 잘못된 것
			verifyNoInteractions(userService);
		}

		private void expectError(UserCreateRequest request, int status, String code) throws Exception {
			mvc.perform(post(USERS_URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(request)))
					.andExpect(status().is(status))
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.code").value(code));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/users/me/account")
	class MyAccount {

		@Test
		@DisplayName("계정 정보를 내려준다")
		void getMyAccount_authenticated_returnsAccount() throws Exception {
			given(userService.getMyAccount(USER_ID)).willReturn(AccountResponse.builder()
					.realName("홍길동")
					.phone("821012345678")
					.email(EMAIL)
					.build());

			mvc.perform(get(USERS_URL + "/me/account").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.realName").value("홍길동"))
					.andExpect(jsonPath("$.data.phone").value("821012345678"))
					.andExpect(jsonPath("$.data.email").value(EMAIL));
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void getMyAccount_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(get(USERS_URL + "/me/account"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("COMMON_401"));

			verifyNoInteractions(userService);
		}
	}

	@Nested
	@DisplayName("GET /api/v1/users/me")
	class MyProfile {

		@Test
		@DisplayName("프로필과 카운터 4종을 내려준다")
		void getMyProfile_authenticated_returnsFourCounters() throws Exception {
			given(userService.getMyProfile(USER_ID)).willReturn(MyProfileResponse.builder()
					.id(USER_ID)
					.nickname(NICKNAME)
					.profileImageUrl(IMAGE_URL)
					.gender(Gender.MALE)
					.heightCm(168)
					.weightKg(58)
					.grade(UserGrade.RECORD)
					.followerCount(201)
					.postCount(60)
					.itemCount(16)
					.wishCount(4)
					.build());

			mvc.perform(get(USERS_URL + "/me").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.nickname").value(NICKNAME))
					.andExpect(jsonPath("$.data.gender").value("MALE"))
					.andExpect(jsonPath("$.data.followerCount").value(201))
					.andExpect(jsonPath("$.data.postCount").value(60))
					.andExpect(jsonPath("$.data.itemCount").value(16))
					.andExpect(jsonPath("$.data.wishCount").value(4));
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/users/me")
	class UpdateProfile {

		@Test
		@DisplayName("수정에 성공하면 id 를 내려준다")
		void updateProfile_validRequest_returnsId() throws Exception {
			given(userService.updateProfile(eq(USER_ID), any(UserUpdateRequest.class)))
					.willReturn(UserUpdateResponse.builder().id(USER_ID).build());

			mvc.perform(patch(USERS_URL + "/me")
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(updateRequest(builder -> {}))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id").value(USER_ID));
		}

		@Test
		@DisplayName("사진 키는 null 로 보낼 수 있다")
		void updateProfile_nullImageKey_returnsOk() throws Exception {
			given(userService.updateProfile(eq(USER_ID), any(UserUpdateRequest.class)))
					.willReturn(UserUpdateResponse.builder().id(USER_ID).build());

			// 기본 이미지로 되돌리는 유일한 방법이라 검증에서 막으면 화면이 동작하지 않는다
			mvc.perform(patch(USERS_URL + "/me")
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(updateRequest(builder -> builder.profileImageKey(null)))))
					.andExpect(status().isOk());
		}

		@Test
		@DisplayName("닉네임이 비면 400 이고 서비스를 호출하지 않는다")
		void updateProfile_blankNickname_returnsBadRequest() throws Exception {
			expectError(updateRequest(builder -> builder.nickname(" ")), 400, "COMMON_400");

			verifyNoInteractions(userService);
		}

		@Test
		@DisplayName("성별이 없으면 400 이다")
		void updateProfile_nullGender_returnsBadRequest() throws Exception {
			// 프로필 입력값 중 사진만 선택이다. 생략을 허용하면 null 의 의미가 두 곳으로 갈린다
			expectError(updateRequest(builder -> builder.gender(null)), 400, "COMMON_400");
		}

		@Test
		@DisplayName("키가 범위를 넘으면 400 이다")
		void updateProfile_heightOutOfRange_returnsBadRequest() throws Exception {
			expectError(updateRequest(builder -> builder.heightCm(201)), 400, "COMMON_400");
		}

		@Test
		@DisplayName("닉네임이 중복이면 409 를 반환한다")
		void updateProfile_duplicateNickname_returnsConflict() throws Exception {
			willThrow(new CustomException(UserErrorCode.USER_409_NICKNAME))
					.given(userService).updateProfile(eq(USER_ID), any(UserUpdateRequest.class));

			expectError(updateRequest(builder -> {}), 409, "USER_409_NICKNAME");
		}

		private void expectError(UserUpdateRequest request, int status, String code) throws Exception {
			mvc.perform(patch(USERS_URL + "/me")
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(request)))
					.andExpect(status().is(status))
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.code").value(code));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/users/{userId}")
	class OtherProfile {

		@Test
		@DisplayName("카운터 3종과 팔로우·차단 상태를 내려주고 성별은 싣지 않는다")
		void getUserProfile_otherUser_returnsThreeCounters() throws Exception {
			given(userService.getUserProfile(USER_ID, OTHER_ID)).willReturn(UserProfileResponse.builder()
					.id(OTHER_ID)
					.nickname(NICKNAME)
					.profileImageUrl(IMAGE_URL)
					.grade(UserGrade.RECORD)
					.heightCm(168)
					.weightKg(58)
					.postCount(60)
					.itemCount(16)
					.followerCount(201)
					.isFollowing(true)
					.isBlocked(false)
					.build());

			mvc.perform(get(USERS_URL + "/" + OTHER_ID).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.postCount").value(60))
					.andExpect(jsonPath("$.data.itemCount").value(16))
					.andExpect(jsonPath("$.data.followerCount").value(201))
					// 래퍼 타입이라야 키가 isFollowing 으로 나간다. boolean 이면 following 이 된다
					.andExpect(jsonPath("$.data.isFollowing").value(true))
					.andExpect(jsonPath("$.data.isBlocked").value(false))
					.andExpect(jsonPath("$.data.gender").doesNotExist());
		}

		@Test
		@DisplayName("없는 사용자면 404 를 반환한다")
		void getUserProfile_unknownUser_returnsNotFound() throws Exception {
			willThrow(new CustomException(UserErrorCode.USER_404))
					.given(userService).getUserProfile(USER_ID, OTHER_ID);

			mvc.perform(get(USERS_URL + "/" + OTHER_ID).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("USER_404"));
		}

		@Test
		@DisplayName("중복 확인 경로는 타인 프로필로 해석되지 않는다")
		void checkEmailPath_notTreatedAsUserId() throws Exception {
			given(userService.checkEmail(EMAIL)).willReturn(available(true));

			// /{userId} 와 경로가 겹치지만 리터럴이 먼저 잡힌다. 아니면 Long 변환에서 400 이 난다
			mvc.perform(get(USERS_URL + "/check-email").param("email", EMAIL))
					.andExpect(status().isOk());
		}
	}

	@Nested
	@DisplayName("공개되지 않은 경로")
	class Protected {

		@Test
		@DisplayName("토큰 없이 인증이 필요한 경로에 접근하면 401 을 JSON 으로 반환한다")
		void protectedPath_noToken_returnsJsonUnauthorized() throws Exception {
			// permitAll 목록에 없는 경로는 anyRequest().authenticated() 가 막는다.
			// HTML 오류 페이지가 나가면 클라이언트가 응답 형식을 두 가지로 처리해야 한다
			mvc.perform(get(USERS_URL + "/me"))
					.andExpect(status().isUnauthorized())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.code").value("COMMON_401"));
		}

		@Test
		@DisplayName("공개된 GET 경로를 POST 로 부르면 401 이다")
		void publicGetPath_withPost_returnsUnauthorized() throws Exception {
			// permitAll 은 메서드까지 묶여 있다. 메서드를 빼고 열면 조회용 경로로 쓰기가 가능해진다
			mvc.perform(post(USERS_URL + "/check-email").param("email", EMAIL))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("COMMON_401"));

			verifyNoInteractions(userService);
		}
	}

	private static UsernamePasswordAuthenticationToken principalOf(Long userId) {
		return new UsernamePasswordAuthenticationToken(new AuthUser(userId), null, List.of());
	}

	/** 유효한 수정 요청을 만들고, 검사하려는 필드 하나만 덮어쓴다. */
	private static UserUpdateRequest updateRequest(Consumer<UserUpdateRequest.UserUpdateRequestBuilder> override) {
		UserUpdateRequest.UserUpdateRequestBuilder builder = UserUpdateRequest.builder()
				.nickname(NICKNAME)
				.profileImageKey("user/2026/09/ab12.jpg")
				.gender(Gender.MALE)
				.heightCm(168)
				.weightKg(58);

		override.accept(builder);
		return builder.build();
	}

	private static AvailabilityResponse available(boolean available) {
		return AvailabilityResponse.builder().available(available).build();
	}

	private static UserCreateRequest.AgreementRequest agreement(AgreementType type, boolean agreed) {
		return UserCreateRequest.AgreementRequest.builder()
				.agreementType(type)
				.agreed(agreed)
				.build();
	}

	/** 유효한 요청을 만들고, 검사하려는 필드 하나만 덮어쓴다. */
	private static UserCreateRequest request(Consumer<UserCreateRequest.UserCreateRequestBuilder> override) {
		UserCreateRequest.UserCreateRequestBuilder builder = UserCreateRequest.builder()
				.email(EMAIL)
				.password("password123")
				.verificationToken("d4f1c0ab9e7b4c2f8a5d3e6b1c9f0a2d")
				.realName("홍길동")
				.phone("821012345678")
				.nickname(NICKNAME)
				.gender(Gender.MALE)
				.heightCm(170)
				.weightKg(64)
				.agreements(Arrays.stream(AgreementType.values())
						.map(type -> agreement(type, true))
						.toList())
				.address(UserCreateRequest.AddressRequest.builder()
						.recipientName("홍길동")
						.phone("010-1234-5678")
						.address("서울특별시 동작구 상도로 369")
						.addressDetail("104동 201호")
						.build());

		override.accept(builder);
		return builder.build();
	}
}
