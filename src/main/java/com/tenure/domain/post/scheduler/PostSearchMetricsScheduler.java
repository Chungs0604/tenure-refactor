package com.tenure.domain.post.scheduler;

import com.tenure.domain.post.service.PostService;
import com.tenure.domain.post.service.RankScorePolicy;
import com.tenure.domain.post.store.PostViewCountBuffer;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 검색 정렬 지표(조회수·추천 순 정렬 키) 배치. 1시간 주기로 돈다
 * (`schema.yaml` 의 `rank_score_batch_interval_minutes`, `DEC-019`·`DEC-023`).
 *
 * Redis 읽기(peek)·DB 반영·Redis 삭제(clear) 순서를 여기서 조립한다 — DB 반영이
 * {@code PostService.applySearchMetrics()} 안에서 끝까지 성공해야 Redis 키를 지운다.
 * 둘을 한 트랜잭션 메서드에 묶지 않은 이유는 {@code PostService} 주석에 적어 뒀다
 * (DB 커넥션이 Redis 왕복 동안 묶이는 것·self-invocation 으로 트랜잭션이 무시되는 것).
 */
@Component
@RequiredArgsConstructor
public class PostSearchMetricsScheduler {

	private final PostService postService;
	private final PostViewCountBuffer postViewCountBuffer;

	@Scheduled(fixedDelay = RankScorePolicy.BATCH_INTERVAL_MS)
	public void syncSearchMetrics() {
		Map<Long, Long> viewDeltas = postViewCountBuffer.peek();

		postService.applySearchMetrics(viewDeltas);

		postViewCountBuffer.clear(viewDeltas.keySet());
	}
}
