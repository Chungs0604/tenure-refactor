package com.tenure.domain.post.scheduler;

import com.tenure.domain.post.service.PostService;
import com.tenure.domain.post.service.RankScorePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 검색 정렬 지표(조회수·추천 순 정렬 키) 배치. 1시간 주기로 돈다
 * (`schema.yaml` 의 `rank_score_batch_interval_minutes`, `DEC-019`·`DEC-023`).
 */
@Component
@RequiredArgsConstructor
public class PostSearchMetricsScheduler {

	private final PostService postService;

	@Scheduled(fixedDelay = RankScorePolicy.BATCH_INTERVAL_MS)
	public void syncSearchMetrics() {
		postService.syncSearchMetrics();
	}
}
