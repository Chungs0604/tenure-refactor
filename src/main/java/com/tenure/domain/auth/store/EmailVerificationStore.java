package com.tenure.domain.auth.store;

import com.tenure.domain.auth.enums.VerificationPurpose;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 이메일 인증번호와 1회용 토큰. RDB 테이블을 두지 않는다 (DEC-009).
 *
 * <p>키 구조가 제약을 대신한다.
 * <ul>
 *   <li>{@code email-verify:{purpose}:{email}} — (email, purpose) 당 1건.
 *       재발급은 {@code SET} 덮어쓰기라 이전 인증번호가 자동으로 무효화된다</li>
 *   <li>{@code email-verify-token:{token}} — 인증 완료 증명.
 *       호출자가 DB 반영을 확인한 뒤 {@link #deleteToken} 으로 지운다(본문 참고)</li>
 * </ul>
 *
 * <p>email 로 만드는 키는 전부 소문자로 정규화한다 — {@code user.email} UNIQUE 가
 * {@code utf8mb4_0900_ai_ci} collation 으로 대소문자를 무시하는데 Redis 키는 바이트 비교라,
 * 정규화하지 않으면 발송·대조·시도 제한이 전부 대소문자에 따라 다른 키를 본다.
 */
@Component
@RequiredArgsConstructor
public class EmailVerificationStore {

	/** 인증번호 수명. 무차별 대입 창을 좁힌다. */
	public static final Duration CODE_TTL = Duration.ofMinutes(3);
	/** 토큰 수명. 가입 플로우(약관 → 주소 검색 → 상세 주소 → 프로필)를 마칠 여유를 준다. */
	public static final Duration TOKEN_TTL = Duration.ofMinutes(10);
	/** 인증번호 대조 허용 횟수. 넘으면 코드 자체를 무효화해 재발송부터 받게 한다. */
	public static final int MAX_CONFIRM_ATTEMPTS = 5;
	/** 재발송 쿨다운. 연속 클릭으로 메일함이 도배되는 것을 막는다. */
	public static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);
	/** 일일 발송 한도 집계 기간. */
	public static final Duration SEND_DAILY_WINDOW = Duration.ofDays(1);
	/** 일일 발송 한도. 메일 발송이 어뷰징 통로가 되는 것을 막는다(design-audit Q7). */
	public static final int MAX_DAILY_SEND = 5;

	private static final String CODE_KEY = "email-verify:%s:%s";
	private static final String TOKEN_KEY = "email-verify-token:%s";
	/** 소비 흔적. 만료(410)와 이미 사용됨(409)을 구분하려고 남긴다 — 단순 삭제만으로는 둘 다 "키 없음" 이다. */
	private static final String USED_KEY = "email-verify-token-used:%s";
	private static final String ATTEMPT_KEY = "email-verify-attempt:%s:%s";
	private static final String SEND_COOLDOWN_KEY = "email-verify-cooldown:%s:%s";
	private static final String SEND_COUNT_KEY = "email-verify-sendcount:%s:%s";
	private static final int CODE_BOUND = 1_000_000;

	private final StringRedisTemplate redis;
	private final SecureRandom random = new SecureRandom();

	/** 인증번호를 새로 발급한다. 기존 것이 있으면 덮어써서 무효화한다. */
	public String issueCode(String email, VerificationPurpose purpose) {
		String code = "%06d".formatted(random.nextInt(CODE_BOUND));
		redis.opsForValue().set(codeKey(email, purpose), code, CODE_TTL);
		return code;
	}

	/** 입력한 인증번호가 맞는지 본다. 없으면(만료) {@code null} 과 구분하기 위해 호출자가 존재를 먼저 확인한다. */
	public String findCode(String email, VerificationPurpose purpose) {
		return redis.opsForValue().get(codeKey(email, purpose));
	}

	/** 인증번호를 지운다. 대조 실패가 한도를 넘었을 때 코드 자체를 무효화해 재발송부터 받게 한다. */
	public void deleteCode(String email, VerificationPurpose purpose) {
		redis.delete(codeKey(email, purpose));
	}

	/**
	 * 인증번호 대조 실패 횟수를 센다. 코드와 같은 TTL 로 묶는다 — 코드가 바뀌거나 만료되면
	 * 이전 시도 횟수가 다음 코드에 넘어오면 안 된다.
	 *
	 * @return 이번 실패를 반영한 누적 횟수
	 */
	public long incrementConfirmAttempts(String email, VerificationPurpose purpose) {
		String key = attemptKey(email, purpose);
		Long count = redis.opsForValue().increment(key);
		if (count != null && count == 1L) {
			redis.expire(key, CODE_TTL);
		}
		return count == null ? 0L : count;
	}

	/** 대조에 성공했을 때 누적된 실패 횟수를 지운다. */
	public void clearConfirmAttempts(String email, VerificationPurpose purpose) {
		redis.delete(attemptKey(email, purpose));
	}

	/** 재전송 쿨다운 중인지 본다. */
	public boolean isInSendCooldown(String email, VerificationPurpose purpose) {
		return Boolean.TRUE.equals(redis.hasKey(sendCooldownKey(email, purpose)));
	}

	/** 오늘 보낸 횟수가 일일 한도를 넘었는지 본다. */
	public boolean exceedsDailySendLimit(String email, VerificationPurpose purpose) {
		String count = redis.opsForValue().get(sendCountKey(email, purpose));
		return count != null && Long.parseLong(count) >= MAX_DAILY_SEND;
	}

	/** 발송에 성공한 뒤 쿨다운을 걸고 일일 발송 횟수를 하나 늘린다. */
	public void markSent(String email, VerificationPurpose purpose) {
		redis.opsForValue().set(sendCooldownKey(email, purpose), "1", SEND_COOLDOWN);

		String key = sendCountKey(email, purpose);
		Long count = redis.opsForValue().increment(key);
		if (count != null && count == 1L) {
			redis.expire(key, SEND_DAILY_WINDOW);
		}
	}

	/**
	 * 인증 완료를 증명하는 1회용 토큰을 발급한다.
	 *
	 * <p>인증번호 키·시도 횟수는 함께 지운다 — 같은 번호로 토큰을 여러 개 받지 못하게 한다.
	 */
	public String issueToken(String email, VerificationPurpose purpose) {
		String token = UUID.randomUUID().toString().replace("-", "");
		redis.opsForValue().set(TOKEN_KEY.formatted(token), "%s:%s".formatted(purpose, email), TOKEN_TTL);
		redis.delete(codeKey(email, purpose));
		clearConfirmAttempts(email, purpose);
		return token;
	}

	/**
	 * 토큰이 가리키는 대상을 지우지 않고 확인한다.
	 *
	 * <p>지우지 않는 이유 — 호출자(회원가입·비밀번호 재설정)는 토큰을 검증한 뒤 DB 쓰기를 한다.
	 * 여기서 바로 지우면, DB 쓰기가 그 뒤에 실패해도 토큰은 이미 사라져 재시도(재발송 없이)가
	 * 불가능해진다. 실제 소비는 DB 커밋이 끝난 뒤 {@link #deleteToken} 으로 마무리한다.
	 *
	 * @return 토큰이 증명하는 대상. 없거나 만료됐으면 {@code null}
	 */
	public VerifiedTarget peekToken(String token) {
		String value = redis.opsForValue().get(TOKEN_KEY.formatted(token));
		if (value == null) {
			return null;
		}
		int sep = value.indexOf(':');

		return new VerifiedTarget(
				VerificationPurpose.valueOf(value.substring(0, sep)),
				value.substring(sep + 1)
		);
	}

	/**
	 * 검증을 마친 토큰을 실제로 지운다. DB 커밋이 끝난 뒤에만 호출한다.
	 *
	 * <p>{@link #peekToken} 이 지우지 않고 보기만 하므로, 같은 토큰으로 동시에 들어온 다른 요청도
	 * 검증을 통과할 수 있는 짧은 창이 생긴다 — 가입은 {@code user.email} UNIQUE, 비밀번호 재설정은
	 * 같은 토큰 소유자 본인의 중복 재설정일 뿐이라 둘 다 최종 일관성이 깨지지 않는다.
	 */
	public void deleteToken(String token) {
		Boolean deleted = redis.delete(TOKEN_KEY.formatted(token));
		if (Boolean.TRUE.equals(deleted)) {
			redis.opsForValue().set(USED_KEY.formatted(token), "1", TOKEN_TTL);
		}
	}

	/** 소비돼서 사라진 것인지, 애초에 만료된 것인지 가른다. */
	public boolean wasConsumed(String token) {
		return Boolean.TRUE.equals(redis.hasKey(USED_KEY.formatted(token)));
	}

	private String codeKey(String email, VerificationPurpose purpose) {
		return CODE_KEY.formatted(purpose, normalize(email));
	}

	private String attemptKey(String email, VerificationPurpose purpose) {
		return ATTEMPT_KEY.formatted(purpose, normalize(email));
	}

	private String sendCooldownKey(String email, VerificationPurpose purpose) {
		return SEND_COOLDOWN_KEY.formatted(purpose, normalize(email));
	}

	private String sendCountKey(String email, VerificationPurpose purpose) {
		return SEND_COUNT_KEY.formatted(purpose, normalize(email));
	}

	private String normalize(String email) {
		return email.toLowerCase(Locale.ROOT);
	}

	/** 토큰이 증명하는 대상. purpose 가 다르면 다른 용도로 쓸 수 없다. */
	public record VerifiedTarget(
			VerificationPurpose purpose,
			String email
	) {}
}
