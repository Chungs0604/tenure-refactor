package com.tenure.domain.user.service;

import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.address.repository.ShippingAddressRepository;
import com.tenure.domain.auth.enums.VerificationPurpose;
import com.tenure.domain.auth.service.AuthService;
import com.tenure.domain.block.repository.UserBlockRepository;
import com.tenure.domain.follow.repository.FollowRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.listing.service.ListingService;
import com.tenure.domain.notification.repository.NotificationRepository;
import com.tenure.domain.notification.repository.NotificationSettingRepository;
import com.tenure.domain.offer.service.OfferService;
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.order.repository.TradeOrderRepository;
import com.tenure.domain.payout.repository.PayoutAccountRepository;
import com.tenure.domain.search.enums.RecentViewTargetType;
import com.tenure.domain.search.repository.SearchHistoryRepository;
import com.tenure.domain.search.store.RecentViewStore;
import com.tenure.domain.user.dto.request.UserCreateRequest;
import com.tenure.domain.user.dto.request.UserUpdateRequest;
import com.tenure.domain.user.dto.request.UserWithdrawRequest;
import com.tenure.domain.user.dto.response.AccountResponse;
import com.tenure.domain.user.dto.response.AvailabilityResponse;
import com.tenure.domain.user.dto.response.MyProfileResponse;
import com.tenure.domain.user.dto.response.UserCreateResponse;
import com.tenure.domain.user.dto.response.UserProfileResponse;
import com.tenure.domain.user.dto.response.UserUpdateResponse;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.entity.UserAgreement;
import com.tenure.domain.user.entity.UserWithdrawal;
import com.tenure.domain.user.enums.AgreementType;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.mapper.UserMapper;
import com.tenure.domain.user.repository.UserAgreementRepository;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.domain.user.repository.UserWithdrawalRepository;
import com.tenure.domain.wishlist.repository.WishlistRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import com.tenure.global.security.jwt.JwtTokenProvider;
import com.tenure.global.security.jwt.WithdrawnUserStore;
import com.tenure.global.security.refresh.RefreshTokenStore;
import com.tenure.global.storage.FileStorage;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
	private final UserWithdrawalRepository userWithdrawalRepository;
	private final ShippingAddressRepository shippingAddressRepository;
	private final ItemRepository itemRepository;
	private final WishlistRepository wishlistRepository;
	private final FollowRepository followRepository;
	private final UserBlockRepository userBlockRepository;
	private final TradeOrderRepository tradeOrderRepository;
	private final PayoutAccountRepository payoutAccountRepository;
	private final SearchHistoryRepository searchHistoryRepository;
	private final NotificationRepository notificationRepository;
	private final NotificationSettingRepository notificationSettingRepository;
	private final OfferService offerService;
	private final ListingService listingService;
	private final AuthService authService;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenStore refreshTokenStore;
	private final WithdrawnUserStore withdrawnUserStore;
	private final RecentViewStore recentViewStore;
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

		// 인증 토큰은 여기서 소비 - 앞의 검증을 모두 통과해 실제로 계정을 만들기 직전까지
		// 미뤄야, 약관·중복 검사 실패로 되돌아갔을 때 토큰이 살아있어 재시도할 수 있다
		authService.consumeVerificationToken(
				request.getVerificationToken(), request.getEmail(), VerificationPurpose.SIGN_UP
		);

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
				user, address.getRecipientName(), address.getPhone(), address.getZipcode(),
				address.getAddress(), address.getAddressDetail(),
				address.getDeliveryRequestType(), address.getDeliveryRequest(), true
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

		int itemCount = itemRepository.countOwnedItem(userId);
		int wishCount = wishlistRepository.countByUserId(userId);

		return userMapper.toMyProfileResponse(
				user,
				toImageUrl(user.getProfileImageKey()),
				itemCount, wishCount
		);
	}

	/** 프로필 수정. 닉네임을 바꿀 때만 중복을 확인한다. */
	@Transactional
	@Loggable("프로필 수정")
	public UserUpdateResponse updateProfile(Long userId, UserUpdateRequest request) {
		User user = findUser(userId);

		// 본인 닉네임 빼고 검사 — 본인의 이전 닉네임은 그대로 사용 가능
		if (userRepository.existsOtherNickname(request.getNickname(), userId)) {
			log.warn("다른 사용자가 쓰고 있는 닉네임입니다. nickname={}", request.getNickname());
			throw new CustomException(UserErrorCode.USER_409_NICKNAME);
		}

		user.updateProfile(
				request.getNickname(),
				request.getProfileImageKey(),
				request.getGender(),
				request.getHeightCm(),
				request.getWeightKg()
		);

		return UserUpdateResponse.builder()
				.id(user.getId())
				.build();
	}

	/**
	 * 타인 프로필 조회.
	 *
	 * 조회가 곧 '최근 본 사용자' 의 입력이다. 자기 프로필을 열었을 때는 남기지 않는다 —
	 * 그 목록은 남을 찾아가는 용도라 자신이 끼면 화면이 이상해진다.
	 *
	 * {viwer = 보는사람(나), userId = 타인}
	 */
	@Loggable("타인 프로필 조회")
	public UserProfileResponse getUserProfile(Long viewerId, Long userId) {

		// 타인 조회
		User user = findUser(userId);

		boolean self = userId.equals(viewerId);

		// 본인 조회인 경우 '최근 본 사용자'에 추가 안함
		if (!self) {
			recentViewStore.add(viewerId, RecentViewTargetType.USER, userId);
		}

		int itemCount = itemRepository.countOwnedItem(userId);

		// 단축 평가로 본인 조회일 때는 팔로우·차단을 조회조차 하지 않는다
		return userMapper.toUserProfileResponse(
				user,
				toImageUrl(user.getProfileImageKey()),
				itemCount,
				!self && followRepository.existsFollow(viewerId, userId),
				!self && userBlockRepository.existsBlock(viewerId, userId)
		);
	}

	/**
	 * 회원탈퇴 (사유 수집 + 즉시 익명화). 탈퇴를 막지 않음 — 대기 중 제안을 정리하고 연관 데이터를
	 * 하드 삭제한 뒤 계정을 익명화함. 진행 중 거래가 있으면 막음
	 */
	@Transactional
	@Loggable("회원탈퇴")
	public void withdraw(Long userId, UserWithdrawRequest request) {
		LocalDateTime now = LocalDateTime.now();

		// 구매자·판매자 어느 쪽이든 아직 끝나지 않은 거래가 있으면 탈퇴 불가
		if (tradeOrderRepository.existsInProgressByUser(userId, OrderStatus.FINISHED)) {
			log.info("진행 중인 거래가 있어 탈퇴할 수 없음 userId={}", userId);
			throw new CustomException(UserErrorCode.USER_409_ACTIVE_TRADE);
		}

		// 내가 소유한 판매중 아이템은 미판매로 내리고 등록·대기 제안을 정리함.
		// item → offer → user 잠금 순서를 지켜야 해서 아래 두 단계보다 먼저 옴
		listingService.closeOnSaleItemsForWithdrawal(userId);

		// 내가 보낸·받은 대기 제안을 정리함(결제 해제 + 상대 알림). User 를 잠그기 전에
		// 와야 함 — 수락 쪽은 offer 를 잠근 채로 구매자 User 행에 알림을 남겨 묵시적으로
		// 잠그는데, 순서가 뒤바뀌면 서로 기다리다 교착한다
		//
		// 정리 도중 상대가 먼저 수락해버린 제안이 있으면 true 가 돌아옴 — 그럼 탈퇴를 막아야 함
		if (offerService.cancelAndRejectPendingForWithdrawal(userId)) {
			log.info("제안 정리 중 먼저 생긴 거래가 있어 탈퇴할 수 없음 userId={}", userId);
			throw new CustomException(UserErrorCode.USER_409_ACTIVE_TRADE);
		}

		// User 행을 여기서 처음 읽고 동시에 잠금 — 동시 중복 탈퇴 요청이 뒤처진 쪽을 여기서
		// 막음. 먼저 평범하게 읽어 두면 영속성 컨텍스트가 그 값을 그대로 돌려줘 잠근 보람이
		// 없으니, 이 트랜잭션에서 User 를 읽는 유일한 지점이어야 함
		User user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 사용자입니다. userId={}", userId);
					return new CustomException(UserErrorCode.USER_404);
				});

		// 이미 탈퇴한 계정인지 확인
		if (user.isWithdrawn()) {
			log.info("이미 탈퇴한 계정이 다시 탈퇴를 시도함 userId={}", userId);
			throw new CustomException(UserErrorCode.USER_409_WITHDRAWN);
		}

		// 탈퇴 사유를 남김
		userWithdrawalRepository.save(UserWithdrawal.of(user, request.getReason(), now));

		// 행은 지우지 않고 개인정보만 치환함 — 이전 소유자 표시가 거래 기록에 남아야 함.
		// 아래 하드 삭제 호출들보다 먼저 와야 함 — @Modifying(clearAutomatically=true) 라
		// 실행 직후 영속성 컨텍스트를 통째로 비움
		user.anonymize(now);

		// 개인정보에 해당하는 연관 데이터를 하드 삭제함. 거래 스냅샷(주문에 복사된 배송지 등)은
		// 이미 따로 떠 있어 원본을 지워도 거래 기록이 끊기지 않음
		shippingAddressRepository.deleteAllByUserId(userId);
		payoutAccountRepository.deleteAllByUserId(userId);
		searchHistoryRepository.deleteAllByUserId(userId);
		notificationRepository.deleteAllByUserId(userId);
		notificationSettingRepository.deleteAllByUserId(userId);
		wishlistRepository.deleteAllByUserId(userId);

		// Redis 반영은 커밋 후로 미룸 — 커밋 전에 실행하면, 이후 커밋이 실패해 DB 가 롤백돼도
		// 이미 끊은 refresh token·차단 표시는 되돌릴 수 없어 계정은 ACTIVE 인데 로그인만
		// 막히는 상태가 됨
		afterCommit(() -> {
			// Redis 에 남은 최근 열람 이력도 함께 지움
			recentViewStore.deleteAll(userId);

			// 남은 refresh token 을 전부 끊음
			refreshTokenStore.revokeAll(userId);

			// access token 은 자연 만료 전까지 계속 유효하므로, Redis 에 탈퇴 표시를 남겨 막음
			withdrawnUserStore.markWithdrawn(userId, jwtTokenProvider.accessTokenTtl());
		});
	}

	// 트랜잭션이 끝까지 커밋된 뒤에만 실행함. 트랜잭션 없이 불리면(단위 테스트 등) 동기화가
	// 활성화돼 있지 않으니 그냥 바로 실행함 — ChatWebSocketService 와 같은 패턴
	private void afterCommit(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					action.run();
				}
			});
		} else {
			action.run();
		}
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
