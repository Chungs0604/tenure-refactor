package com.tenure.domain.post.store;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
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
	 * 배치가 호출한다. 쌓인 키를 전부 꺼내고 지운다 — 다음 주기가 겹쳐 세지 않도록 조회와 삭제를
	 * 키 단위로 묶는다({@code GETDEL}). 사이에 들어온 조회 1건을 놓칠 수 있지만 다음 주기에
	 * 다시 잡히므로 조회수 성격상 허용한다.
	 */
	public Map<Long, Long> drain() {
		Map<Long, Long> deltas = new LinkedHashMap<>();

		// connection.scan(ScanOptions) 는 deprecated 지만, 대체로 안내된 scan(KeyScanOptions) 는
		// KeyScanOptions 를 만드는 public 빌더가 없어 그대로 쓴다
		try (Cursor<byte[]> cursor = redis.executeWithStickyConnection(connection ->
				connection.scan(ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(200).build()))) {
			while (cursor.hasNext()) {
				String key = new String(cursor.next(), StandardCharsets.UTF_8);
				String value = redis.opsForValue().getAndDelete(key);
				if (value != null) {
					Long postId = Long.valueOf(key.substring(KEY_PREFIX.length()));
					deltas.put(postId, Long.parseLong(value));
				}
			}
		}

		return deltas;
	}
}
