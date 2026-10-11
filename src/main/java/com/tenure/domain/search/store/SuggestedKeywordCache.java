package com.tenure.domain.search.store;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 추천 검색어(API-206) 캐시. 전체 사용자가 같은 값을 본다 — 개인화가 아니라 사이트 전체의
 * 인기 검색어 집계라 사용자별 키가 아니다(`DEC-020`).
 *
 * 매 요청마다 {@code search_history} 를 GROUP BY 로 집계하면 트래픽이 늘수록 느려지므로
 * TTL 동안은 캐시를 그대로 돌려준다.
 */
@Component
@RequiredArgsConstructor
public class SuggestedKeywordCache {

	private static final String KEY = "suggested-keywords";

	/** 명세에 정해진 값이 아니다 — 트렌드가 그 안에 완전히 바뀌는 일은 드물어 넉넉히 잡았다. */
	private static final Duration TTL = Duration.ofMinutes(10);

	private final StringRedisTemplate redis;

	/** 캐시 미스면 빈 목록 — 호출 쪽이 집계해서 {@link #put} 으로 채운다. */
	public List<String> get() {
		List<String> cached = redis.opsForList().range(KEY, 0, -1);
		return cached == null ? List.of() : cached;
	}

	public void put(List<String> keywords) {
		redis.delete(KEY);
		if (!keywords.isEmpty()) {
			redis.opsForList().rightPushAll(KEY, keywords);
			redis.expire(KEY, TTL);
		}
	}
}
