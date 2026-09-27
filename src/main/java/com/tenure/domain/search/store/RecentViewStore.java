package com.tenure.domain.search.store;

import com.tenure.domain.search.enums.RecentViewTargetType;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 최근 열람. RDB 테이블을 두지 않는다 (DEC-014).
 *
 * <p>Sorted Set 의 구조가 보관 정책을 대신한다. score 를 열람 시각으로 두면 재열람이 새 행이 아니라
 * score 갱신이 되어 {@code (user, targetType, target)} 당 1건이 유지되고, 상한을 넘긴 것은
 * 순위로 잘라낸다. 열람 이력은 다시 볼 일도 남길 이유도 없어 기간을 따로 정하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RecentViewStore {

	/**
	 * 보관 건수. 화면이 최근 몇 건만 쓰므로 넉넉히 잡고 그 위는 버린다 —
	 * 상한이 없으면 열람할수록 키가 커진다. 명세에 정해진 값은 아니다.
	 */
	private static final long MAX_SIZE = 20;

	private static final String KEY = "recent-view:%d:%s";

	private final StringRedisTemplate redis;

	/**
	 * 열람을 기록한다. 재열람이면 시각만 갱신된다.
	 *
	 * @param userId 목록의 주인(보는 사람)
	 * @param targetId 열람한 대상
	 */
	public void add(Long userId, RecentViewTargetType targetType, Long targetId) {
		String key = KEY.formatted(userId, targetType);

		redis.opsForZSet().add(key, String.valueOf(targetId), Instant.now().toEpochMilli());
		// 오래된 쪽(score 낮은 쪽)을 잘라낸다. 음수 순위라 건수가 상한 이하면 아무것도 지우지 않는다
		redis.opsForZSet().removeRange(key, 0, -(MAX_SIZE + 1));
	}
}
