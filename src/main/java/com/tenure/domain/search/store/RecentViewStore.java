package com.tenure.domain.search.store;

import com.tenure.domain.search.enums.RecentViewTargetType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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

	/** 회원탈퇴 시 이 사용자의 열람 이력을 전부 지움. 대상 유형마다 키가 갈려 있어 하나씩 지움 */
	public void deleteAll(Long userId) {
		// 유형별로 키가 따로 있어 한 번에 지우는 명령이 없음 — 유형 수만큼 반복함
		for (RecentViewTargetType targetType : RecentViewTargetType.values()) {
			redis.delete(KEY.formatted(userId, targetType));
		}
	}

	/**
	 * 최근 열람한 대상 id 목록. score(열람 시각)가 높은 순 — 가장 최근 본 것부터다.
	 * '최근 본 사용자'(API-203)와 '방금 보신 OOTD와 유사한 게시물'(API-202)의 입력이다.
	 */
	public List<Long> findRecentTargetIds(Long userId, RecentViewTargetType targetType, int limit) {
		Set<String> members = redis.opsForZSet()
				.reverseRange(KEY.formatted(userId, targetType), 0, limit - 1);

		if (members == null || members.isEmpty()) {
			return List.of();
		}
		return members.stream().map(Long::valueOf).toList();
	}

	/**
	 * 개별 삭제 (API-208, '최근 본 사용자' X). 이미 없는 멤버를 지워도(열람 안 했거나 상한에
	 * 잘렸거나) 멱등하게 성공한다.
	 */
	public void remove(Long userId, RecentViewTargetType targetType, Long targetId) {
		redis.opsForZSet().remove(KEY.formatted(userId, targetType), String.valueOf(targetId));
	}

	/**
	 * 유형 하나만 전체 삭제 (API-209, '최근 본 사용자' 지우기). {@code deleteAll} 과 달리
	 * 화면의 '최근 본 사용자' 섹션만 지운다 — '방금 보신 OOTD' 추천 입력(POST 유형)은 남긴다.
	 */
	public void deleteByType(Long userId, RecentViewTargetType targetType) {
		redis.delete(KEY.formatted(userId, targetType));
	}
}
