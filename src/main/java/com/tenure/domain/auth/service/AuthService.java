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
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.LogMask;
import com.tenure.global.logging.Loggable;
import com.tenure.global.mail.EmailSender;
import com.tenure.global.security.jwt.JwtTokenProvider;
import com.tenure.global.security.refresh.RefreshTokenStore;
import com.tenure.global.storage.FileStorage;
import io.jsonwebtoken.Claims;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static com.tenure.domain.auth.enums.VerificationPurpose.*;

/**
 * 로그인·토큰 발급과 이메일 인증.
 *
 * 인증번호·토큰·refresh token 은 전부 Redis 에 있다. RDB 는 계정 조회에만 쓴다.
 *
 * 예외를 던지기 전에 왜 끊겼는지를 남긴다. 어떤 ErrorCode 로 끊겼는지는
 * ServiceLoggingAspect 가 이미 찍으므로, 여기서는 그것만으로 알 수 없는
 * 판단 근거(어떤 값 때문인지)를 보탠다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

	private final EmailVerificationStore verificationStore;
	private final UserRepository userRepository;
	private final EmailSender emailSender;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenStore refreshTokenStore;
	private final FileStorage fileStorage;

	/**
	 * 로그인
	 *
	 * 미가입과 비밀번호 불일치를 다른 코드로 답한다
	 * 화면에 `가입되지 않은 이메일입니다` 문구가 별도로 있어 두 경우를 가려 보여준다.
	 */
	@Loggable("로그인")
	public LoginResponse login(LoginRequest request) {

		User user = userRepository.findByEmail(request.getEmail())
				.orElseThrow(() -> {
					log.warn("가입 이력이 없는 이메일로 로그인을 시도했다. email={}", request.getEmail());
					return new CustomException(AuthErrorCode.AUTH_404_EMAIL);
				});

		// 탈퇴 계정. 익명화로 이메일이 치환되므로 보통은 위에서 걸리지만 파기 전 상태를 대비
		if (user.isWithdrawn()) {
			log.warn("탈퇴한 계정으로 로그인을 시도했다. userId={}", user.getId());
			throw new CustomException(AuthErrorCode.AUTH_404_EMAIL);
		}

		// 비밀번호 대조. 입력값은 로그에 남기지 않는다
		if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
			log.warn("비밀번호가 일치하지 않는다. userId={}", user.getId());
			throw new CustomException(AuthErrorCode.AUTH_401_CREDENTIAL);
		}

		// `로그인 상태 유지` 는 선택 입력이라 없으면 기본 수명(14일)을 사용
		// '로그인 상태 유지' = 90일
		boolean keepLoggedIn = Boolean.TRUE.equals(request.getKeepLoggedIn());

		// RefreshToken 발급
		JwtTokenProvider.IssuedRefreshToken refresh = jwtTokenProvider
				.issueRefreshToken(user.getId(), keepLoggedIn);

		// accessToken 발급
		String accessToken = jwtTokenProvider.issueAccessToken(user.getId());

		// 발급한 tokenId 를 남긴다. 살아 있는지가 재발급·로그아웃의 판단 근거다
		refreshTokenStore.save(user.getId(), refresh.tokenId(), refresh.ttl());

		LoginResponse.UserSummary userSummary = LoginResponse.UserSummary.builder()
				.id(user.getId())
				.nickname(user.getNickname())
				.profileImageUrl(toProfileImageUrl(user.getProfileImageKey()))
				.grade(user.getGrade())
				.build();

		return LoginResponse.builder()
				.accessToken(accessToken)
				.refreshToken(refresh.token())
				.user(userSummary)
				.build();
	}

	/**
	 * 로그아웃 - 이 사용자의 refresh token 을 전부 무효화한다.
	 *
	 * access token 은 무효화하지 않는다 — 무상태 검증이라 저장소를 보지 않으며,
	 * 남은 수명(짧다)이 지나면 재발급이 막혀 자연히 끊긴다.
	 */
	@Loggable("로그아웃")
	public void logout(Long userId) {
		refreshTokenStore.revokeAll(userId);
	}

	/**
	 * 액세스 토큰 재발급.
	 *
	 * 서명만 보고 발급하지 않는다. 저장소에 tokenId 가 살아 있어야 함
	 * 그래야 로그아웃한 토큰으로 계속 재발급받는 것을 막을 수 있음.
	 */
	@Loggable("액세스 토큰 재발급")
	public TokenReissueResponse reissue(TokenReissueRequest request) {

		// 서명·만료 검증
		Claims claims = jwtTokenProvider.parseOrNull(request.getRefreshToken());

		if (claims == null) {
			log.warn("서명이 깨졌거나 만료된 토큰으로 재발급을 시도했다");
			throw new CustomException(AuthErrorCode.AUTH_401_TOKEN);
		}

		// tid 클레임이 없으면 access token 이다. 재발급 입력으로 쓸 수 없음
		// RefreshToken에만 tid(tokenId) 존재
		String tokenId = jwtTokenProvider.getTokenId(claims);

		if (tokenId == null) {
			log.warn("refresh token 이 아닌 토큰으로 재발급을 시도했다");
			throw new CustomException(AuthErrorCode.AUTH_401_TOKEN);
		}

		Long userId = jwtTokenProvider.getUserId(claims);

		// 로그아웃으로 이미 끊긴 토큰인지 확인
		if (!refreshTokenStore.isAlive(userId, tokenId)) {
			log.warn("무효화된 refresh token 으로 재발급을 시도했다. userId={}", userId);
			throw new CustomException(AuthErrorCode.AUTH_401_TOKEN);
		}

		// 탈퇴 여부 확인. withdraw() 의 Redis 반영(refreshTokenStore.revokeAll 등)은 커밋 후로
		// 미뤄지는데, 그 사이나 Redis 장애로 일부만 실행되면 isAlive() 만으로는 탈퇴자를 걸러내지
		// 못한다 — DB 를 직접 봐야 확실하다
		User user = userRepository.findById(userId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 사용자의 refresh token 으로 재발급을 시도했다. userId={}", userId);
					return new CustomException(AuthErrorCode.AUTH_401_TOKEN);
				});

		if (user.isWithdrawn()) {
			log.warn("탈퇴한 계정이 refresh token 으로 재발급을 시도했다. userId={}", userId);
			throw new CustomException(AuthErrorCode.AUTH_401_TOKEN);
		}

		return TokenReissueResponse.builder()
				.accessToken(jwtTokenProvider.issueAccessToken(userId))
				.build();
	}

	/**
	 * 비밀번호 재설정
	 *
	 * 토큰을 먼저 소비 — 유효하지 않으면 계정을 조회하지도 않는다.
	 * 토큰에 용도가 묶여 있어 회원가입용 토큰으로는 비밀번호를 바꿀 수 없음.
	 */
	@Transactional
	@Loggable("비밀번호 재설정")
	public void resetPassword(PasswordResetRequest request) {

		// 1회용 토큰 소비 (용도·대상 이메일 검사)
		consumeVerificationToken(request.getVerificationToken(), request.getEmail(), PASSWORD_RESET);

		User user = userRepository.findByEmail(request.getEmail())
				.orElseThrow(() -> {
					log.warn("인증은 통과했지만 계정을 찾을 수 없다. email={}", request.getEmail());
					return new CustomException(UserErrorCode.USER_404);
				});

		user.changePassword(passwordEncoder.encode(request.getNewPassword()));

		// 기존 세션을 끊는다 — 비밀번호를 바꾼 이유가 탈취라면 남은 refresh token 으로 계속 들어올 수 있다
		refreshTokenStore.revokeAll(user.getId());
	}

	/**
	 * 인증번호를 발송한다.
	 *
	 * 기존 인증번호가 있으면 덮어써서 무효화한다.
	 *
	 * <p>DB 쓰기가 없는 메서드다. 클래스 레벨 readOnly 트랜잭션을 그대로 물려받으면
	 * {@code existsByEmail} 조회가 잡은 커넥션을 SMTP 발송(동기, 느릴 수 있다)이
	 * 끝날 때까지 들고 있게 된다. 트랜잭션을 걷어내 조회는 리포지토리가 자체적으로
	 * 열고 닫는 짧은 트랜잭션에 맡긴다.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@Loggable("인증번호 발송")
	public EmailVerificationSendResponse sendCode(EmailVerificationSendRequest request) {

		// 재전송 어뷰징 차단(design-audit Q7) — 계정 조회보다 먼저 봐서 불필요한 DB 조회도 아낀다
		if (verificationStore.isInSendCooldown(request.getEmail(), request.getPurpose())) {
			log.warn("재전송 쿨다운 중이다. email={} purpose={}", request.getEmail(), request.getPurpose());
			throw new CustomException(AuthErrorCode.AUTH_429_CODE_RESEND_COOLDOWN);
		}
		if (verificationStore.exceedsDailySendLimit(request.getEmail(), request.getPurpose())) {
			log.warn("일일 발송 한도를 넘었다. email={} purpose={}", request.getEmail(), request.getPurpose());
			throw new CustomException(AuthErrorCode.AUTH_429_CODE_DAILY_LIMIT);
		}

		// 용도에 따라 가입 여부를 반대로 본다 — 가입은 없어야 하고, 비밀번호 찾기는 있어야 함
		boolean alreadyJoined = userRepository.existsByEmail(request.getEmail());

		if (request.getPurpose() == SIGN_UP && alreadyJoined) {
			log.warn("가입용 인증번호 요청이지만 이미 가입된 이메일이다. email={}", request.getEmail());
			throw new CustomException(UserErrorCode.USER_409_EMAIL);
		}
		if (request.getPurpose() == PASSWORD_RESET && !alreadyJoined) {
			log.warn("비밀번호 찾기 요청이지만 가입 이력이 없다. email={}", request.getEmail());
			throw new CustomException(AuthErrorCode.AUTH_404_EMAIL);
		}

		// 인증번호 발급. 같은 (email, purpose) 의 이전 번호는 이 시점에 무효화
		String code = verificationStore.issueCode(request.getEmail(), request.getPurpose());

		// 메일 발송
		emailSender.sendVerificationCode(request.getEmail(), code);

		// 발송에 성공한 뒤에만 쿨다운·일일 횟수를 기록한다 — 메일 발송 실패까지 한도에 넣으면
		// 인프라 장애가 사용자의 재시도 기회를 깎아먹는다
		verificationStore.markSent(request.getEmail(), request.getPurpose());

		return EmailVerificationSendResponse.builder()
				.expiresAt(LocalDateTime.now().plus(EmailVerificationStore.CODE_TTL))
				.build();
	}

	/**
	 * 인증번호를 확인하고 1회용 토큰을 발급한다.
	 */
	@Loggable("인증번호 확인")
	public EmailVerificationConfirmResponse confirmCode(EmailVerificationConfirmRequest request) {

		// 발급된 번호를 꺼낸다. 없으면 TTL 이 지난 것
		String issued = verificationStore.findCode(request.getEmail(), request.getPurpose());

		if (issued == null) {
			log.warn("인증번호가 만료됐거나 발급된 적이 없다. email={} purpose={}", request.getEmail(), request.getPurpose());
			throw new CustomException(AuthErrorCode.AUTH_410_CODE);
		}

		// 입력값 대조. 번호 자체는 로그에 남기지 않는다
		if (!issued.equals(request.getCode())) {

			// 틀려도 코드 키가 안 지워지면 같은 코드로 무제한 대입이 가능하다. 한도를 넘으면
			// 코드 자체를 지워 만료와 같은 상태로 만든다 — 공격자는 재발송(쿨다운·일일 한도가 막음)
			// 부터 다시 받아야 한다
			long attempts = verificationStore.incrementConfirmAttempts(request.getEmail(), request.getPurpose());
			if (attempts >= EmailVerificationStore.MAX_CONFIRM_ATTEMPTS) {
				verificationStore.deleteCode(request.getEmail(), request.getPurpose());
				verificationStore.clearConfirmAttempts(request.getEmail(), request.getPurpose());
				log.warn("인증번호 대조 실패가 한도를 넘어 코드를 무효화했다. email={} purpose={}",
						request.getEmail(), request.getPurpose());
				throw new CustomException(AuthErrorCode.AUTH_410_CODE);
			}

			log.warn("인증번호 불일치 email={} purpose={}", request.getEmail(), request.getPurpose());
			throw new CustomException(AuthErrorCode.AUTH_400_CODE);
		}

		// 1회용 토큰 발급. 인증번호 키·시도 횟수는 함께 지워져 같은 번호로 토큰을 또 받을 수 없음
		String token = verificationStore.issueToken(request.getEmail(), request.getPurpose());

		return EmailVerificationConfirmResponse.builder()
				.verificationToken(token)
				.expiresAt(LocalDateTime.now().plus(EmailVerificationStore.TOKEN_TTL))
				.build();
	}

	/**
	 * 인증 토큰을 검증한다. 회원가입·비밀번호 재설정이 공유한다.
	 *
	 * <p>검증만 하고 토큰은 바로 지우지 않는다 — 호출자(signUp, resetPassword)는 이 검증 다음에
	 * DB 쓰기를 하는데, 여기서 즉시 지우면 그 DB 쓰기가 나중에 실패(닉네임 동시 충돌, 계정 소실 등)해
	 * 롤백돼도 토큰은 이미 사라진 뒤라 재발송 없이 재시도할 수 없다. 실제 삭제는 호출자의 트랜잭션이
	 * 커밋된 뒤로 미룬다 — 롤백되면 삭제 자체가 실행되지 않아 토큰이 살아남는다.
	 *
	 * <p>이 때문에 같은 토큰으로 동시에 들어온 다른 요청도 커밋 전까지는 검증을 통과할 수 있는 짧은
	 * 창이 생긴다. 회원가입은 {@code user.email} UNIQUE 제약이 둘 중 하나를 걸러내 최종 일관성이
	 * 깨지지 않고, 비밀번호 재설정은 토큰 보유자 본인이 비밀번호를 중복으로 바꾸는 것뿐이라 보안상
	 * 문제가 되지 않는다.
	 */
	@Loggable("인증 토큰 검증")
	public void consumeVerificationToken(
			@LogMask String token, String email,
			VerificationPurpose purpose
	) {
		// 회원가입 or 비밀번호 재설정을 하는 회원(예비)을 조회. 토큰은 지우지 않고 본다
		EmailVerificationStore.VerifiedTarget target = verificationStore.peekToken(token);

		// 없다면 이미 쓴 것인지 만료된 것인지 가른다. 흔적 키로 구분
		if (target == null) {
			boolean consumed = verificationStore.wasConsumed(token);

			log.warn("인증 토큰을 쓸 수 없다. email={} purpose={} 사유={}",
					email, purpose, consumed ? "이미 사용됨" : "만료");

			throw new CustomException(consumed
					? AuthErrorCode.AUTH_409_TOKEN_CONSUMED
					: AuthErrorCode.AUTH_410_VERIFICATION_TOKEN);
		}

		// 용도 바인딩 검사 — 가입용 토큰으로 비밀번호를 바꾸지 못하게 막음
		if (target.purpose() != purpose) {
			log.warn("인증 토큰의 용도가 다릅니다. 토큰={} 요청={}", target.purpose(), purpose);
			throw new CustomException(AuthErrorCode.AUTH_400_PURPOSE_MISMATCH);
		}

		// 대상 이메일 검사 — 남의 토큰으로 다른 이메일을 가입시키는 경로를 막음
		if (!target.email().equalsIgnoreCase(email)) {
			log.warn("인증 토큰의 대상 이메일이 다릅니다. 토큰대상={} 요청={}", target.email(), email);
			throw new CustomException(AuthErrorCode.AUTH_401_VERIFICATION_TOKEN);
		}

		// 실제 소비는 호출자의 DB 트랜잭션이 커밋된 뒤로 미룸. withdraw() 와 같은 패턴
		afterCommit(() -> verificationStore.deleteToken(token));
	}

	// 트랜잭션이 끝까지 커밋된 뒤에만 실행함. 트랜잭션 없이 불리면(단위 테스트 등) 동기화가
	// 활성화돼 있지 않으니 그냥 바로 실행함 — UserService.withdraw() 와 같은 패턴
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

	/** 저장된 것은 키이고 화면에 필요한 것은 URL 이다. 미등록이면 null 그대로 내려준다. */
	private String toProfileImageUrl(String profileImageKey) {
		return profileImageKey == null ? null : fileStorage.toUrl(profileImageKey);
	}
}
