package com.tenure.global.security.refresh;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
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
	private static final int SCAN_COUNT = 100;

	private final StringRedisTemplate redis;

	// Redis에 refreshToken 저장
	public void save(Long userId, String tokenId, Duration ttl) {
		redis.opsForValue().set(KEY.formatted(userId, tokenId), "1", ttl);
	}

	public boolean isAlive(Long userId, String tokenId) {
		return Boolean.TRUE.equals(redis.hasKey(KEY.formatted(userId, tokenId)));
	}

	/**
	 * 로그아웃 (API-002). 해당 사용자의 refresh token 을 전부 끊는다.
	 *
	 * <p>기기 단위로 끊지 않는 이유 — 로그아웃 요청은 access token 으로만 인증되고 access token 에는
	 * {@code tokenId} 클레임이 없다. 어느 기기의 토큰인지 알 방법이 없으므로 전부 지운다.
	 * 기기별 세션 화면이 생기면 그때 DEC-010 을 재검토한다.
	 *
	 * <p>{@code KEYS} 대신 {@code SCAN} 을 쓴다 — 키가 늘어도 Redis 를 블로킹하지 않는다.
	 */
	public void revokeAll(Long userId) {
		ScanOptions options = ScanOptions.scanOptions()
				.match(KEY.formatted(userId, "*"))
				.count(SCAN_COUNT)
				.build();

		List<String> keys = new ArrayList<>();
		try (Cursor<String> cursor = redis.scan(options)) {
			cursor.forEachRemaining(keys::add);
		}

		if (!keys.isEmpty()) {
			redis.delete(keys);
		}
	}
}
