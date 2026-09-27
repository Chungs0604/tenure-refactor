package com.tenure.domain.user.service;

import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.address.repository.ShippingAddressRepository;
import com.tenure.domain.auth.enums.VerificationPurpose;
import com.tenure.domain.auth.service.AuthService;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.user.dto.request.UserCreateRequest;
import com.tenure.domain.user.dto.response.AccountResponse;
import com.tenure.domain.user.dto.response.AvailabilityResponse;
import com.tenure.domain.user.dto.response.MyProfileResponse;
import com.tenure.domain.user.dto.response.UserCreateResponse;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.entity.UserAgreement;
import com.tenure.domain.user.enums.AgreementType;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.mapper.UserMapper;
import com.tenure.domain.user.repository.UserAgreementRepository;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.domain.wishlist.repository.WishlistRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import com.tenure.global.security.jwt.JwtTokenProvider;
import com.tenure.global.security.refresh.RefreshTokenStore;
import com.tenure.global.storage.FileStorage;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입과 중복 확인, 계정·프로필 조회와 수정.
 *
 * 예외를 던지기 전에 판단 근거를 남긴다 — ErrorCode 자체는
 * ServiceLoggingAspect 가 찍는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	/** 화면의 '동의 받기' 3종. 하나라도 빠지면 가입시키지 않는다. */
	private static final Set<AgreementType> REQUIRED_AGREEMENTS = EnumSet.allOf(AgreementType.class);

	private final UserRepository userRepository;
	private final UserAgreementRepository userAgreementRepository;
	private final ShippingAddressRepository shippingAddressRepository;
	private final ItemRepository itemRepository;
	private final WishlistRepository wishlistRepository;
	private final AuthService authService;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenStore refreshTokenStore;
	private final FileStorage fileStorage;
	private final UserMapper userMapper;

	/** 이메일 중복 확인. 중복이어도 예외를 던지지 않는다 — 화면이 안내 문구를 띄운다. */
	@Loggable("이메일 중복 확인")
	public AvailabilityResponse checkEmail(String email) {
		return AvailabilityResponse.builder()
				.available(!userRepository.existsByEmail(email))
				.build();
	}

	/** 닉네임 중복 확인 */
	@Loggable("닉네임 중복 확인")
	public AvailabilityResponse checkNickname(String nickname) {
		return AvailabilityResponse.builder()
				.available(!userRepository.existsByNickname(nickname))
				.build();
	}

	/**
	 * 회원가입. 약관·기본 배송지를 같은 트랜잭션에서 만든다.
	 */
	@Transactional
	@Loggable("회원가입")
	public UserCreateResponse signUp(UserCreateRequest request) {
		// 인증 토큰부터 소비 - 유효하지 않으면 중복 검사도 하지 않는다
		authService.consumeVerificationToken(
				request.getVerificationToken(), request.getEmail(), VerificationPurpose.SIGN_UP
		);

		// 필수 약관 3종이 모두 있는지 검사
		validateAgreements(request.getAgreements());

		// 유니크 제약 선검사.
		if (userRepository.existsByEmail(request.getEmail())) {
			log.warn("이미 가입된 이메일 입니다. email={}", request.getEmail());
			throw new CustomException(UserErrorCode.USER_409_EMAIL);
		}
		if (userRepository.existsByNickname(request.getNickname())) {
			log.warn("이미 가입된 닉네임 입니다. nickname={}", request.getNickname());
			throw new CustomException(UserErrorCode.USER_409_NICKNAME);
		}

		// 계정 생성. 비밀번호는 해시해서 넘김
		User signUpUser = User.signUp(
				request.getEmail(),
				passwordEncoder.encode(request.getPassword()),
				request.getRealName(),
				request.getPhone(),
				request.getNickname(),
				request.getProfileImageKey(),
				request.getGender(),
				request.getHeightCm(),
				request.getWeightKg()
		);

		User user = userRepository.save(signUpUser);

		// 약관 동의 기록. 법적 증빙이라 append-only(update, delete 금지)
		request.getAgreements().forEach(agreement ->
		{
			UserAgreement agree = UserAgreement.agree(user, agreement.getAgreementType());
			userAgreementRepository.save(agree);
		});

		// 첫 배송지는 기본 배송지로 등록
		UserCreateRequest.AddressRequest address = request.getAddress();

		ShippingAddress shippingAddress = ShippingAddress.register(
				user, address.getRecipientName(), address.getPhone(), address.getAddress(),
				address.getAddressDetail(), address.getDeliveryRequest(), true
		);

		shippingAddressRepository.save(shippingAddress);

		// 가입 직후 자동 로그인. keepLoggedIn 은 로그인 화면에만 있으므로 기본 수명 사용
		JwtTokenProvider.IssuedRefreshToken refresh = jwtTokenProvider
				.issueRefreshToken(user.getId(), false);

		// Redis에 refreshToken 저장
		refreshTokenStore.save(user.getId(), refresh.tokenId(), refresh.ttl());
		String accessToken = jwtTokenProvider.issueAccessToken(user.getId());

		return UserCreateResponse.builder()
				.id(user.getId())
				.accessToken(accessToken)
				.refreshToken(refresh.token())
				.build();
	}

	/** 계정 정보 조회. 프로필과 달리 실명·휴대폰·이메일만 내려준다. */
	@Loggable("계정 정보 조회")
	public AccountResponse getMyAccount(Long userId) {
		return userMapper.toAccountResponse(findUser(userId));
	}

	/**
	 * 내 프로필 조회.
	 *
	 * 팔로워·게시물 수는 카운터 컬럼을 그대로 읽지만 아이템·위시는 COUNT 로 셈
	 */
	@Loggable("내 프로필 조회")
	public MyProfileResponse getMyProfile(Long userId) {
		User user = findUser(userId);

		int itemCount = itemRepository.countRegisteredItem(userId);
		int wishCount = wishlistRepository.countByUserId(userId);

		return userMapper.toMyProfileResponse(
				user,
				toImageUrl(user.getProfileImageKey()),
				itemCount, wishCount
		);
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 사용자입니다. userId={}", userId);
					return new CustomException(UserErrorCode.USER_404);
				});
	}

	/** 저장소 키를 화면용 URL 로 바꾼다. 사진이 없으면 URL 도 없다 — 기본 이미지는 화면이 붙인다. */
	private String toImageUrl(String imageKey) {
		return imageKey == null ? null : fileStorage.toUrl(imageKey);
	}

	/** 필수 약관이 모두 포함됐는지 확인. 동의 여부(true) 자체는 DTO 가 검증. */
	private void validateAgreements(List<UserCreateRequest.AgreementRequest> agreements) {

		Set<AgreementType> agreed = agreements.stream()
				.map(UserCreateRequest.AgreementRequest::getAgreementType)
				.collect(Collectors.toCollection(() -> EnumSet.noneOf(AgreementType.class)));

		if (!agreed.containsAll(REQUIRED_AGREEMENTS)) {

			Set<AgreementType> missing = EnumSet.copyOf(REQUIRED_AGREEMENTS);
			missing.removeAll(agreed);

			log.warn("필수 약관이 누락되었습니다. 누락={}", missing);
			throw new CustomException(UserErrorCode.USER_400_AGREEMENT);
		}
	}
}
