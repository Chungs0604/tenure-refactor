package com.tenure.domain.order.scheduler;

import com.tenure.domain.order.service.OrderService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 배송 완료 +72시간 무응답 자동 구매 확정 배치. 5분 주기로 돌며 늦어도 그 안에는 확정 처리됨.
 *
 * OfferExpirationScheduler 와 같은 주기다 — '자동 구매 확정까지 23시간 44분 남음' 표시가
 * 분 단위로 보이는 화면이라 같은 정밀도를 맞춘다
 */
@Component
@RequiredArgsConstructor
public class OrderAutoConfirmScheduler {

	private static final long FIVE_MINUTES_MS = 5 * 60 * 1000L;

	/** OrderService 내부의 날짜 계산과 같은 기준을 맞춘다 — JVM 기본 시간대에 흔들리면 안 됨 */
	private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

	private final OrderService orderService;

	@Scheduled(fixedDelay = FIVE_MINUTES_MS)
	public void autoConfirmDueOrders() {
		orderService.autoConfirmDue(LocalDateTime.now(ZONE_SEOUL));
	}
}
