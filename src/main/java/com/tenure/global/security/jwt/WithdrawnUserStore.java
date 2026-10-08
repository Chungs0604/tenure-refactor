package com.tenure.global.security.jwt;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 탈퇴한 사용자 표시. access token 은 서명·만료만으로 유효성을 판단해(무상태) 탈퇴해도
 * 자연 만료 전까지 계속 쓸 수 있다 — refresh token 에 적용한 기준을 access token 에도
 * 적용해(DEC-010), 탈퇴 시점에 Redis 에 표시해 두고 인증 경계(JwtAuthenticationFilter·
 * StompAuthChannelInterceptor)에서 함께 확인한다.
 *
 * <p>토큰 하나하나를 식별하지 않고 userId 단위로 막는다 — access token 에는 고유 식별자(tid)가
 * 없어 애초에 개별 토큰을 가릴 방법이 없다. 탈퇴 후엔 재로그인도 막혀 있어(AuthService) 새
 * 토큰이 나올 일이 없으므로, TTL 을 access token 최대 수명으로 두면 그 사이 발급된 어떤
 * 토큰도 전부 덮는다 — 이후엔 키가 자연 소멸해 정리 배치가 필요 없다.
 */
@Component
@RequiredArgsConstructor
public class WithdrawnUserStore {

	private static final String KEY = "withdrawn:%d";

	private final StringRedisTemplate redis;

	public void markWithdrawn(Long userId, Duration ttl) {
		redis.opsForValue().set(KEY.formatted(userId), "1", ttl);
	}

	public boolean isWithdrawn(Long userId) {
		return Boolean.TRUE.equals(redis.hasKey(KEY.formatted(userId)));
	}
}
