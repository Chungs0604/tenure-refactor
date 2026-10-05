package com.tenure.domain.order.scheduler;

import com.tenure.domain.order.service.OrderService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 정산 배치. 영업일 계산이 날짜 단위라 분 단위로 돌 필요가 없어 하루 한 번만 돈다.
 *
 * 새벽 1시 — 자정 직후 날짜가 바뀌고도 여유를 두어, 그 시각 전후로 생성된 settlement 이
 * 날짜 경계에서 하루 밀리는 걸 피한다.
 *
 * cron 트리거는 Asia/Seoul 기준으로 돌지만, 그 안에서 넘기는 LocalDateTime.now() 는 기본적으로
 * JVM 기본 시간대를 쓴다 — 서버가 UTC 로 떠 있으면 '오늘'의 기준이 9시간 어긋나 정산이 하루씩
 * 밀릴 수 있어 여기서도 명시적으로 고정한다
 */
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

	private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

	private final OrderService orderService;

	@Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
	public void settleDueOrders() {
		orderService.settleDue(LocalDateTime.now(ZONE_SEOUL));
	}
}
