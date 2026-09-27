package com.tenure.domain.user.service;

import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.address.repository.ShippingAddressRepository;
import com.tenure.domain.auth.enums.VerificationPurpose;
import com.tenure.domain.auth.exception.AuthErrorCode;
import com.tenure.domain.auth.service.AuthService;
import com.tenure.domain.block.repository.UserBlockRepository;
import com.tenure.domain.follow.repository.FollowRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.search.enums.RecentViewTargetType;
import com.tenure.domain.search.store.RecentViewStore;
import com.tenure.domain.user.dto.request.UserCreateRequest;
import com.tenure.domain.user.dto.request.UserUpdateRequest;
import com.tenure.domain.user.dto.response.AccountResponse;
import com.tenure.domain.user.dto.response.AvailabilityResponse;
import com.tenure.domain.user.dto.response.MyProfileResponse;
import com.tenure.domain.user.dto.response.UserCreateResponse;
import com.tenure.domain.user.dto.response.UserProfileResponse;
import com.tenure.domain.user.dto.response.UserUpdateResponse;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.entity.UserAgreement;
import com.tenure.domain.user.enums.AgreementType;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.enums.UserGrade;
import com.tenure.domain.user.enums.UserStatus;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.mapper.UserMapper;
import com.tenure.domain.user.repository.UserAgreementRepository;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.domain.wishlist.repository.WishlistRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import com.tenure.global.security.jwt.JwtTokenProvider;
import com.tenure.global.security.refresh.RefreshTokenStore;
import com.tenure.global.storage.FileStorage;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
	private static final String IMAGE_KEY = "user/2026/09/ab12.jpg";
	private static final String IMAGE_URL = "https://cdn.tenure.app/user/2026/09/ab12.jpg";

	@Mock private UserRepository userRepository;
	@Mock private UserAgreementRepository userAgreementRepository;
	@Mock private ShippingAddressRepository shippingAddressRepository;
	@Mock private ItemRepository itemRepository;
	@Mock private WishlistRepository wishlistRepository;
	@Mock private FollowRepository followRepository;
	@Mock private UserBlockRepository userBlockRepository;
	@Mock private AuthService authService;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private JwtTokenProvider jwtTokenProvider;
	@Mock private RefreshTokenStore refreshTokenStore;
	@Mock private RecentViewStore recentViewStore;
	@Mock private FileStorage fileStorage;

	/** mock 하지 않는다 — mock 하면 엔티티에 필드를 넣고 응답에 안 실어도 테스트가 통과한다. */
	@Spy private UserMapper userMapper = Mappers.getMapper(UserMapper.class);

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

	@Nested
	@DisplayName("계정 정보 조회")
	class MyAccount {

		@Test
		@DisplayName("실명·휴대폰·이메일을 내려준다")
		void getMyAccount_existingUser_returnsAccount() {
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(profileUser()));

			AccountResponse response = userService.getMyAccount(USER_ID);

			assertThat(response.getRealName()).isEqualTo("홍길동");
			assertThat(response.getPhone()).isEqualTo("821012345678");
			assertThat(response.getEmail()).isEqualTo(EMAIL);
		}

		@Test
		@DisplayName("없는 사용자면 USER_404 예외가 발생한다")
		void getMyAccount_unknownUser_throwsNotFound() {
			given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> userService.getMyAccount(USER_ID))
					.isInstanceOf(CustomException.class)
					.extracting(UserServiceTest::errorCodeOf)
					.isEqualTo(UserErrorCode.USER_404);
		}
	}

	@Nested
	@DisplayName("내 프로필 조회")
	class MyProfile {

		@Test
		@DisplayName("팔로워·게시물은 카운터 컬럼으로, 아이템·위시는 COUNT 로 채운다")
		void getMyProfile_existingUser_returnsFourCounters() {
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(profileUser()));
			given(itemRepository.countRegisteredItem(USER_ID)).willReturn(16);
			given(wishlistRepository.countByUserId(USER_ID)).willReturn(4);
			given(fileStorage.toUrl(IMAGE_KEY)).willReturn(IMAGE_URL);

			MyProfileResponse response = userService.getMyProfile(USER_ID);

			assertThat(response.getFollowerCount()).isEqualTo(201);
			assertThat(response.getPostCount()).isEqualTo(60);
			assertThat(response.getItemCount()).isEqualTo(16);
			assertThat(response.getWishCount()).isEqualTo(4);
		}

		@Test
		@DisplayName("성별과 체형을 함께 내려준다")
		void getMyProfile_existingUser_includesGenderAndBody() {
			givenMyProfileSucceeds();

			MyProfileResponse response = userService.getMyProfile(USER_ID);

			// 성별은 화면에 없지만 수정 폼의 초기값으로 필요하다 (DEC-101)
			assertThat(response.getGender()).isEqualTo(Gender.MALE);
			assertThat(response.getHeightCm()).isEqualTo(168);
			assertThat(response.getWeightKg()).isEqualTo(58);
			assertThat(response.getGrade()).isEqualTo(UserGrade.BASIC);
		}

		@Test
		@DisplayName("저장소 키를 URL 로 바꿔 내려준다")
		void getMyProfile_hasImage_returnsUrlNotKey() {
			givenMyProfileSucceeds();

			MyProfileResponse response = userService.getMyProfile(USER_ID);

			assertThat(response.getProfileImageUrl()).isEqualTo(IMAGE_URL);
		}

		@Test
		@DisplayName("사진이 없으면 URL 이 null 이고 저장소를 호출하지 않는다")
		void getMyProfile_noImage_returnsNullUrl() {
			User user = profileUser();
			ReflectionTestUtils.setField(user, "profileImageKey", null);
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

			MyProfileResponse response = userService.getMyProfile(USER_ID);

			// toUrl(null) 은 '.../null' 을 만든다. 기본 이미지는 화면이 붙인다
			assertThat(response.getProfileImageUrl()).isNull();
			verifyNoInteractions(fileStorage);
		}

		private void givenMyProfileSucceeds() {
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(profileUser()));
			given(fileStorage.toUrl(IMAGE_KEY)).willReturn(IMAGE_URL);
		}
	}

	@Nested
	@DisplayName("프로필 수정")
	class UpdateProfile {

		@Test
		@DisplayName("보낸 값으로 프로필을 덮어쓴다")
		void updateProfile_validRequest_overwritesProfile() {
			User user = profileUser();
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
			given(userRepository.existsOtherNickname("Yujin", USER_ID)).willReturn(false);

			UserUpdateResponse response = userService.updateProfile(USER_ID, updateRequest("Yujin", "user/new.jpg"));

			assertThat(response.getId()).isEqualTo(USER_ID);
			assertThat(user.getNickname()).isEqualTo("Yujin");
			assertThat(user.getProfileImageKey()).isEqualTo("user/new.jpg");
			assertThat(user.getGender()).isEqualTo(Gender.FEMALE);
			assertThat(user.getHeightCm()).isEqualTo(170);
			assertThat(user.getWeightKg()).isEqualTo(64);
		}

		@Test
		@DisplayName("사진 키를 null 로 보내면 기본 이미지로 되돌린다")
		void updateProfile_nullImageKey_resetsToDefaultImage() {
			User user = profileUser();
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
			given(userRepository.existsOtherNickname(NICKNAME, USER_ID)).willReturn(false);

			userService.updateProfile(USER_ID, updateRequest(NICKNAME, null));

			assertThat(user.getProfileImageKey()).isNull();
		}

		@Test
		@DisplayName("닉네임을 그대로 보내면 자기 행은 중복으로 보지 않는다")
		void updateProfile_sameNickname_passesDuplicateCheck() {
			User user = profileUser();
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
			// 대소문자를 무시하는 collation 이라 자기 닉네임도 existsByNickname 에는 걸린다
			given(userRepository.existsOtherNickname(NICKNAME, USER_ID)).willReturn(false);

			userService.updateProfile(USER_ID, updateRequest(NICKNAME, IMAGE_KEY));

			assertThat(user.getNickname()).isEqualTo(NICKNAME);
		}

		@Test
		@DisplayName("다른 사용자가 쓰는 닉네임이면 USER_409_NICKNAME 이고 아무것도 바꾸지 않는다")
		void updateProfile_duplicateNickname_throwsConflict() {
			User user = profileUser();
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
			given(userRepository.existsOtherNickname("Yujin", USER_ID)).willReturn(true);

			assertThatThrownBy(() -> userService.updateProfile(USER_ID, updateRequest("Yujin", IMAGE_KEY)))
					.isInstanceOf(CustomException.class)
					.extracting(UserServiceTest::errorCodeOf)
					.isEqualTo(UserErrorCode.USER_409_NICKNAME);

			assertThat(user.getNickname()).isEqualTo(NICKNAME);
		}

		@Test
		@DisplayName("없는 사용자면 USER_404 이고 중복 검사조차 하지 않는다")
		void updateProfile_unknownUser_throwsNotFound() {
			given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> userService.updateProfile(USER_ID, updateRequest(NICKNAME, IMAGE_KEY)))
					.isInstanceOf(CustomException.class)
					.extracting(UserServiceTest::errorCodeOf)
					.isEqualTo(UserErrorCode.USER_404);

			verify(userRepository, times(0)).existsOtherNickname(any(), any());
		}
	}

	@Nested
	@DisplayName("타인 프로필 조회")
	class OtherProfile {

		private static final Long VIEWER_ID = 2L;

		@Test
		@DisplayName("카운터 3종과 팔로우·차단 상태를 내려준다")
		void getUserProfile_otherUser_returnsThreeCountersAndRelation() {
			givenOtherProfile();
			given(followRepository.existsFollow(VIEWER_ID, USER_ID)).willReturn(true);
			given(userBlockRepository.existsBlock(VIEWER_ID, USER_ID)).willReturn(false);

			UserProfileResponse response = userService.getUserProfile(VIEWER_ID, USER_ID);

			assertThat(response.getPostCount()).isEqualTo(60);
			assertThat(response.getItemCount()).isEqualTo(16);
			assertThat(response.getFollowerCount()).isEqualTo(201);
			assertThat(response.getIsFollowing()).isTrue();
			assertThat(response.getIsBlocked()).isFalse();
		}

		@Test
		@DisplayName("등급과 체형을 내려준다")
		void getUserProfile_otherUser_includesGradeAndBody() {
			givenOtherProfile();
			given(followRepository.existsFollow(VIEWER_ID, USER_ID)).willReturn(false);
			given(userBlockRepository.existsBlock(VIEWER_ID, USER_ID)).willReturn(false);

			UserProfileResponse response = userService.getUserProfile(VIEWER_ID, USER_ID);

			// 헤더 2행이 '레코드 사용자 | 168cm · 58kg' 다 (DEC-101)
			assertThat(response.getGrade()).isEqualTo(UserGrade.BASIC);
			assertThat(response.getHeightCm()).isEqualTo(168);
			assertThat(response.getWeightKg()).isEqualTo(58);
		}

		@Test
		@DisplayName("조회하면 최근 본 사용자에 기록한다")
		void getUserProfile_otherUser_recordsRecentView() {
			givenOtherProfile();
			given(followRepository.existsFollow(VIEWER_ID, USER_ID)).willReturn(false);
			given(userBlockRepository.existsBlock(VIEWER_ID, USER_ID)).willReturn(false);

			userService.getUserProfile(VIEWER_ID, USER_ID);

			verify(recentViewStore).add(VIEWER_ID, RecentViewTargetType.USER, USER_ID);
		}

		@Test
		@DisplayName("자기 프로필이면 최근 열람에 남기지 않고 팔로우·차단도 조회하지 않는다")
		void getUserProfile_self_skipsRecentViewAndRelation() {
			givenOtherProfile();

			UserProfileResponse response = userService.getUserProfile(USER_ID, USER_ID);

			// 최근 본 사용자는 남을 찾아가는 목록이라 자신이 끼면 화면이 이상해진다.
			// 팔로우·차단은 물어볼 필요가 없어 쿼리도 나가지 않는다
			verifyNoInteractions(recentViewStore, followRepository, userBlockRepository);
			assertThat(response.getIsFollowing()).isFalse();
			assertThat(response.getIsBlocked()).isFalse();
		}

		@Test
		@DisplayName("탈퇴 계정이면 닉네임을 '탈퇴한 사용자' 로 내려준다")
		void getUserProfile_withdrawnUser_masksNickname() {
			User user = profileUser();
			ReflectionTestUtils.setField(user, "status", UserStatus.WITHDRAWN);
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
			given(itemRepository.countRegisteredItem(USER_ID)).willReturn(16);
			given(fileStorage.toUrl(IMAGE_KEY)).willReturn(IMAGE_URL);
			given(followRepository.existsFollow(VIEWER_ID, USER_ID)).willReturn(false);
			given(userBlockRepository.existsBlock(VIEWER_ID, USER_ID)).willReturn(false);

			UserProfileResponse response = userService.getUserProfile(VIEWER_ID, USER_ID);

			assertThat(response.getNickname()).isEqualTo("탈퇴한 사용자");
		}

		@Test
		@DisplayName("없는 사용자면 USER_404 이고 최근 열람에 남기지 않는다")
		void getUserProfile_unknownUser_throwsNotFound() {
			given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> userService.getUserProfile(VIEWER_ID, USER_ID))
					.isInstanceOf(CustomException.class)
					.extracting(UserServiceTest::errorCodeOf)
					.isEqualTo(UserErrorCode.USER_404);

			verifyNoInteractions(recentViewStore);
		}

		private void givenOtherProfile() {
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(profileUser()));
			given(itemRepository.countRegisteredItem(USER_ID)).willReturn(16);
			given(fileStorage.toUrl(IMAGE_KEY)).willReturn(IMAGE_URL);
		}
	}

	/** 조회용 고정 사용자. 카운터 컬럼은 가입 시 0 이라 화면 예시 값으로 채워 둔다. */
	private static User profileUser() {
		User user = User.signUp(
				EMAIL, HASHED_PASSWORD, "홍길동", "821012345678",
				NICKNAME, IMAGE_KEY, Gender.MALE, 168, 58
		);
		ReflectionTestUtils.setField(user, "id", USER_ID);
		ReflectionTestUtils.setField(user, "followerCount", 201);
		ReflectionTestUtils.setField(user, "postCount", 60);
		return user;
	}

	private static UserUpdateRequest updateRequest(String nickname, String profileImageKey) {
		return UserUpdateRequest.builder()
				.nickname(nickname)
				.profileImageKey(profileImageKey)
				.gender(Gender.FEMALE)
				.heightCm(170)
				.weightKg(64)
				.build();
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
