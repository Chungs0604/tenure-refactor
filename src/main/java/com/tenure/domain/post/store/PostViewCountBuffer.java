package com.tenure.domain.post.store;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 게시물 조회수 버퍼. 조회마다 {@code post.view_count} 를 UPDATE 하면 인기 게시물 한 행에 쓰기가
 * 몰리고 읽기 경로에 락 경합이 생긴다 (DEC-019, {@code RecentViewStore} 와 같은 기준).
 *
 * <p>조회는 Redis 카운터에만 쌓고, 배치가 주기적으로 꺼내 컬럼에 반영한다.
 */
@Component
@RequiredArgsConstructor
public class PostViewCountBuffer {

	private static final String KEY_PREFIX = "post-view:";
	private static final String KEY_FORMAT = KEY_PREFIX + "%d";

	private final StringRedisTemplate redis;

	/** 조회를 기록한다. */
	public void increment(Long postId) {
		redis.opsForValue().increment(KEY_FORMAT.formatted(postId));
	}

	/**
	 * 배치가 호출한다. 쌓인 값을 읽기만 하고 지우지 않는다 — DB 반영(Post.view_count 갱신)이
	 * 성공한 뒤 {@link #clear} 로 지워야, UPDATE 가 실패해 트랜잭션이 롤백돼도 그 주기의
	 * 조회수가 Redis 에 그대로 남아 다음 주기에 다시 반영된다.
	 *
	 * 읽기와 지우기를 묶어서 하면(옛 GETDEL 방식) DB 반영이 실패해도 Redis 쪽은 이미 지워진
	 * 뒤라 그 주기에 쌓인 조회수 전체가 사라진다 — 조회 1건 유실(허용 범위)과는 다른 크기의 손실이다.
	 */
	public Map<Long, Long> peek() {
		Map<Long, Long> deltas = new LinkedHashMap<>();

		// connection.scan(ScanOptions) 는 deprecated 지만, 대체로 안내된 scan(KeyScanOptions) 는
		// KeyScanOptions 를 만드는 public 빌더가 없어 그대로 쓴다
		try (Cursor<byte[]> cursor = redis.executeWithStickyConnection(connection ->
				connection.scan(ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(200).build()))) {
			while (cursor.hasNext()) {
				String key = new String(cursor.next(), StandardCharsets.UTF_8);
				String value = redis.opsForValue().get(key);
				if (value != null) {
					Long postId = Long.valueOf(key.substring(KEY_PREFIX.length()));
					deltas.put(postId, Long.parseLong(value));
				}
			}
		}

		return deltas;
	}

	/** {@link #peek} 이 돌려준 게시물 id 들의 키를 지운다. DB 반영이 끝난 뒤에만 부른다. */
	public void clear(Collection<Long> postIds) {
		if (postIds.isEmpty()) {
			return;
		}
		Set<String> keys = postIds.stream().map(KEY_FORMAT::formatted).collect(Collectors.toSet());
		redis.delete(keys);
	}
}
