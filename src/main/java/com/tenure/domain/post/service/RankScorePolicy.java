package com.tenure.domain.post.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 검색 '추천 순' 정렬 키 계산식의 가중치 (DEC-023).
 *
 * rank_score = log10(1 + save*3 + like*1 + view*0.2) * 0.6 + exp(-경과일/14) * 0.4
 *
 * 값은 상수로 시작한다 — 클릭 로그가 쌓이기 전에는 조정할 근거가 없다. `schema.yaml` 의
 * `rank_score_weight_*` 가 정본이며, 바꿀 때는 거기도 같이 고친다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RankScorePolicy {

	/** 인기(좋아요·저장·조회) 항의 비중 */
	public static final double POPULARITY_WEIGHT = 0.6;

	/** 신선도(게시 경과일) 항의 비중 */
	public static final double FRESHNESS_WEIGHT = 0.4;

	public static final double SAVE_WEIGHT = 3;
	public static final double LIKE_WEIGHT = 1;
	public static final double VIEW_WEIGHT = 0.2;

	/** 신선도가 절반으로 줄어드는 경과일 */
	public static final double FRESHNESS_HALF_LIFE_DAYS = 14;

	/** 배치 주기. view_count 반영과 rank_score 재계산이 이 주기로 함께 돈다 */
	public static final long BATCH_INTERVAL_MS = 60 * 60 * 1000L;
}
