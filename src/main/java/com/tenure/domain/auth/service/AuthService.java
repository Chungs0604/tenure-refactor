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
import com.tenure.global.logging.LogMask;
import com.tenure.global.logging.Loggable;
import com.tenure.global.mail.EmailSender;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.tenure.domain.auth.enums.VerificationPurpose.*;

/**
 * 이메일 인증. 상태는 전부 Redis 에 있고 RDB 는 가입 여부 확인에만 쓴다 (DEC-009).
 *
 * <p>예외를 던지기 전에 <b>왜 끊겼는지</b>를 남긴다. 어떤 ErrorCode 로 끊겼는지는
 * {@code ServiceLoggingAspect} 가 이미 찍으므로, 여기서는 그것만으로 알 수 없는
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

	/**
	 * 인증번호를 발송한다 (API-004).
	 *
	 * <p>기존 인증번호가 있으면 덮어써서 무효화한다.
	 */
	@Loggable("인증번호 발송")
	public EmailVerificationSendResponse sendCode(EmailVerificationSendRequest request) {

		// 용도에 따라 가입 여부를 반대로 본다 — 가입은 없어야 하고, 비밀번호 찾기는 있어야 한다
		boolean alreadyJoined = userRepository.existsByEmail(request.getEmail());

		if (request.getPurpose() == SIGN_UP && alreadyJoined) {
			log.warn("가입용 인증번호 요청이지만 이미 가입된 이메일이다. email={}", request.getEmail());
			throw new CustomException(UserErrorCode.USER_409_EMAIL);
		}
		if (request.getPurpose() == PASSWORD_RESET && !alreadyJoined) {
			log.warn("비밀번호 찾기 요청이지만 가입 이력이 없다. email={}", request.getEmail());
			throw new CustomException(AuthErrorCode.AUTH_404_EMAIL);
		}

		// 인증번호 발급. 같은 (email, purpose) 의 이전 번호는 이 시점에 무효화된다
		String code = verificationStore.issueCode(request.getEmail(), request.getPurpose());

		// 메일 발송. readOnly 트랜잭션이라 커넥션을 오래 잡지 않는다
		emailSender.sendVerificationCode(request.getEmail(), code);

		return EmailVerificationSendResponse.builder()
				.expiresAt(LocalDateTime.now().plus(EmailVerificationStore.CODE_TTL))
				.build();
	}

	/**
	 * 인증번호를 확인하고 1회용 토큰을 발급한다 (API-005).
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
			log.warn("인증번호 불일치 email={} purpose={}", request.getEmail(), request.getPurpose());
			throw new CustomException(AuthErrorCode.AUTH_400_CODE);
		}

		// 1회용 토큰 발급. 인증번호 키는 함께 지워져 같은 번호로 토큰을 또 받을 수 없다
		String token = verificationStore.issueToken(request.getEmail(), request.getPurpose());

		return EmailVerificationConfirmResponse.builder()
				.verificationToken(token)
				.expiresAt(LocalDateTime.now().plus(EmailVerificationStore.TOKEN_TTL))
				.build();
	}

	/**
	 * 인증 토큰을 소비한다. 회원가입·비밀번호 재설정이 공유한다.
	 *
	 * <p>읽으면서 지우므로 두 번 쓸 수 없다.
	 *
	 * @throws CustomException 토큰이 없거나 용도·이메일이 맞지 않을 때
	 */
	@Loggable("인증 토큰 소비")
	public void consumeVerificationToken(
			@LogMask String token, String email,
			VerificationPurpose purpose
	) {
		// 회원가입 or 비밀번호 재설정을 하는 회원(예비)을 조회하면서 1회용 토큰 소비
		EmailVerificationStore.VerifiedTarget target = verificationStore.consumeToken(token);

		// 없다면 이미 쓴 것인지 만료된 것인지 가른다. 흔적 키로 구분한다
		if (target == null) {
			boolean consumed = verificationStore.wasConsumed(token);

			log.warn("인증 토큰을 쓸 수 없다. email={} purpose={} 사유={}",
					email, purpose, consumed ? "이미 사용됨" : "만료");

			throw new CustomException(consumed
					? AuthErrorCode.AUTH_409_TOKEN_CONSUMED
					: AuthErrorCode.AUTH_410_VERIFICATION_TOKEN);
		}

		// 용도 바인딩 검사 — 가입영 토큰으로 비밀번호를 바꾸지 못하게 막는다
		if (target.purpose() != purpose) {
			log.warn("인증 토큰의 용도가 다릅니다. 토큰={} 요청={}", target.purpose(), purpose);
			throw new CustomException(AuthErrorCode.AUTH_400_PURPOSE_MISMATCH);
		}

		// 대상 이메일 검사 — 남의 토큰으로 다른 이메일을 가입시키는 경로를 막는다
		if (!target.email().equalsIgnoreCase(email)) {
			log.warn("인증 토큰의 대상 이메일이 다릅니다. 토큰대상={} 요청={}", target.email(), email);
			throw new CustomException(AuthErrorCode.AUTH_401_VERIFICATION_TOKEN);
		}
	}
}
