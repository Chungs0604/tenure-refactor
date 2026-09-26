package com.tenure.global.security.refresh;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * refresh token 저장소. RDB 테이블을 두지 않는다 (DEC-010).
 *
 * <p>JWT 자체 서명만 쓰면 로그아웃 시 무효화가 불가능해 유출된 토큰이 만료까지 살아있다.
 * 발급한 {@code tokenId} 를 여기 남겨 두고, 재발급 때 살아 있는지 확인한다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

	private static final String KEY = "refresh:%d:%s";

	private final StringRedisTemplate redis;

	// Redis에 refreshToken 저장
	public void save(Long userId, String tokenId, Duration ttl) {
		redis.opsForValue().set(KEY.formatted(userId, tokenId), "1", ttl);
	}

	public boolean isAlive(Long userId, String tokenId) {
		return Boolean.TRUE.equals(redis.hasKey(KEY.formatted(userId, tokenId)));
	}

	/** 로그아웃. 해당 기기의 토큰만 끊는다. */
	public void revoke(Long userId, String tokenId) {
		redis.delete(KEY.formatted(userId, tokenId));
	}
}
