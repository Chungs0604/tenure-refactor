package com.tenure.domain.auth.store;

import com.tenure.domain.auth.enums.VerificationPurpose;
import java.security.SecureRandom;
import java.time.Duration;
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
 *       소비는 {@code GETDEL} 이라 조회와 삭제가 원자적이고 재사용이 구조적으로 불가능하다</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class EmailVerificationStore {

	/** 인증번호 수명. 무차별 대입 창을 좁힌다. */
	public static final Duration CODE_TTL = Duration.ofMinutes(3);
	/** 토큰 수명. 가입 플로우(약관 → 주소 검색 → 상세 주소 → 프로필)를 마칠 여유를 준다. */
	public static final Duration TOKEN_TTL = Duration.ofMinutes(10);

	private static final String CODE_KEY = "email-verify:%s:%s";
	private static final String TOKEN_KEY = "email-verify-token:%s";
	/** 소비 흔적. 만료(410)와 이미 사용됨(409)을 구분하려고 남긴다 — GETDEL 만으로는 둘 다 "키 없음" 이다. */
	private static final String USED_KEY = "email-verify-token-used:%s";
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

	/**
	 * 인증 완료를 증명하는 1회용 토큰을 발급한다.
	 *
	 * <p>인증번호 키는 함께 지운다 — 같은 번호로 토큰을 여러 개 받지 못하게 한다.
	 */
	public String issueToken(String email, VerificationPurpose purpose) {
		String token = UUID.randomUUID().toString().replace("-", "");
		redis.opsForValue().set(TOKEN_KEY.formatted(token), "%s:%s".formatted(purpose, email), TOKEN_TTL);
		redis.delete(codeKey(email, purpose));
		return token;
	}

	/**
	 * 토큰을 소비한다. 읽으면서 지우므로 두 번 쓸 수 없다.
	 *
	 * @return 토큰이 증명하는 대상. 없거나 만료됐으면 {@code null}
	 */
	public VerifiedTarget consumeToken(String token) {
		String value = redis.opsForValue().getAndDelete(TOKEN_KEY.formatted(token));
		if (value == null) {
			return null;
		}
		redis.opsForValue().set(USED_KEY.formatted(token), "1", TOKEN_TTL);
		int sep = value.indexOf(':');

		return new VerifiedTarget(
				VerificationPurpose.valueOf(value.substring(0, sep)),
				value.substring(sep + 1)
		);
	}

	/** 소비돼서 사라진 것인지, 애초에 만료된 것인지 가른다. */
	public boolean wasConsumed(String token) {
		return Boolean.TRUE.equals(redis.hasKey(USED_KEY.formatted(token)));
	}

	private String codeKey(String email, VerificationPurpose purpose) {
		return CODE_KEY.formatted(purpose, email);
	}

	/** 토큰이 증명하는 대상. purpose 가 다르면 다른 용도로 쓸 수 없다. */
	public record VerifiedTarget(
			VerificationPurpose purpose,
			String email
	) {}
}
