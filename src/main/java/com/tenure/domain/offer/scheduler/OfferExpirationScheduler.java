package com.tenure.domain.offer.scheduler;

import com.tenure.domain.offer.service.OfferService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 24시간 무응답 구매 제안 자동 만료 배치. 5분 주기로 돌며 늦어도 그 안에는 만료 처리됨
 */
@Component
@RequiredArgsConstructor
public class OfferExpirationScheduler {

	private static final long FIVE_MINUTES_MS = 5 * 60 * 1000L;

	private final OfferService offerService;

	@Scheduled(fixedDelay = FIVE_MINUTES_MS)
	public void expirePendingOffers() {
		offerService.expirePending(LocalDateTime.now());
	}
}
