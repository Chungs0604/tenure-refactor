package com.tenure.domain.payment.client;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 토스페이먼츠 결제 승인(confirm)·취소(cancel) 호출.
 *
 * 간편결제(토스페이·카카오페이·네이버페이)는 confirm 호출 즉시 최종 결제가 끝난다 — 카드처럼
 * "임시 승인만 잡아두기"가 없다. 그래서 거절·취소·만료로 우리 쪽 결제를 '해제'할 때도 내부
 * 상태만 바꾸는 게 아니라 이 cancel 을 실제로 호출해야 돈이 돌아간다.
 *
 * <p>생성자를 직접 쓴다({@code @RequiredArgsConstructor} 가 아님) — Lombok 은 필드의
 * {@code @Qualifier} 를 생성자 파라미터로 복사해주지 않는다(복사 대상은 {@code @NonNull} 류뿐).
 * 그대로 두면 타입만으로 빈이 해석돼 {@code @Primary} 가 붙은 공용(holiday) 빌더가 조용히
 * 주입된다 — 결제 전용 타임아웃이 적용 안 되는 걸 눈으로 확인하기 어렵다
 */
@Slf4j
@Component
public class TossPaymentClient {

	private static final String ALREADY_CANCELED_CODE = "ALREADY_CANCELED_PAYMENT";

	private final RestClient.Builder restClientBuilder;
	private final TossPaymentProperties properties;
	private final ObjectMapper objectMapper;

	public TossPaymentClient(
			@Qualifier("paymentRestClientBuilder") RestClient.Builder restClientBuilder,
			TossPaymentProperties properties,
			ObjectMapper objectMapper) {
		this.restClientBuilder = restClientBuilder;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	/**
	 * 결제 승인. 토스가 명시적으로 거부한 4xx(HttpClientErrorException)만 Rejected 다.
	 * 타임아웃·연결 끊김·5xx(HttpServerErrorException)는 Unknown 으로 구분한다 — 응답을
	 * 못 받았다고 '안 됐다'고 단정하면, 실제로는 승인됐는데 우리만 실패로 처리해 돈만
	 * 빠지는 상태가 된다. 본문이 빈 200 응답도 Unknown 으로 본다 — paymentKey 를 모르는
	 * 'Success' 는 아무것도 못 하는 성공이라 더 위험하다. paymentKey 는 그 값만으로
	 * confirm·cancel 을 호출할 수 있는 자격증명이라 로그에 남기지 않는다 — orderId 로만 추적한다
	 */
	public ConfirmOutcome confirm(String paymentKey, String orderId, Integer amount) {
		try {
			TossConfirmResponse response = restClientBuilder.build()
					.post()
					.uri(properties.baseUrl() + "/v1/payments/confirm")
					.headers(headers -> headers.setBasicAuth(properties.secretKey(), ""))
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
					.retrieve()
					.body(TossConfirmResponse.class);
			if (response == null || response.paymentKey() == null || response.paymentKey().isBlank()) {
				log.error("토스페이먼츠 결제 승인 응답에 paymentKey 가 없음(결과 불명) orderId={}", orderId);
				return new ConfirmOutcome.Unknown();
			}
			return new ConfirmOutcome.Success(response);
		} catch (HttpClientErrorException e) {
			log.info("토스페이먼츠 결제 승인 거부 orderId={} status={}", orderId, e.getStatusCode());
			return new ConfirmOutcome.Rejected();
		} catch (HttpServerErrorException e) {
			log.warn("토스페이먼츠 결제 승인 중 PG 서버 오류(결과 불명) orderId={} status={}", orderId, e.getStatusCode());
			return new ConfirmOutcome.Unknown();
		} catch (RestClientException e) {
			log.warn("토스페이먼츠 결제 승인 응답을 받지 못함(결과 불명) orderId={}", orderId, e);
			return new ConfirmOutcome.Unknown();
		}
	}

	/**
	 * 결제 취소(전액). 거절·구매자 취소·자동 만료·거래 취소 전부 이 경로로 환불한다.
	 *
	 * 이미 취소된 결제(ALREADY_CANCELED_PAYMENT)는 실패가 아니라 '목표 상태에 이미 도달함'
	 * 으로 본다 — 그렇지 않으면 타임아웃으로 실패 처리된 뒤 재시도할 때마다 이미 취소된
	 * 결제를 다시 취소하려다 영구히 거부당해, 그 제안이 다시는 해제될 수 없는 상태로 굳는다
	 */
	public boolean cancel(String paymentKey, String cancelReason) {
		try {
			restClientBuilder.build()
					.post()
					.uri(properties.baseUrl() + "/v1/payments/" + paymentKey + "/cancel")
					.headers(headers -> headers.setBasicAuth(properties.secretKey(), ""))
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("cancelReason", cancelReason))
					.retrieve()
					.toBodilessEntity();
			return true;
		} catch (HttpStatusCodeException e) {
			if (isAlreadyCanceled(e)) {
				log.info("토스페이먼츠에서 이미 취소된 결제임 — 목표 상태로 보고 성공 처리함 paymentKey={}", mask(paymentKey));
				return true;
			}
			log.warn("토스페이먼츠 결제 취소 거부 paymentKey={} status={}", mask(paymentKey), e.getStatusCode());
			return false;
		} catch (RestClientException e) {
			log.warn("토스페이먼츠 결제 취소 응답을 받지 못함 paymentKey={}", mask(paymentKey), e);
			return false;
		}
	}

	private boolean isAlreadyCanceled(HttpStatusCodeException e) {
		try {
			JsonNode body = objectMapper.readTree(e.getResponseBodyAsString());
			return ALREADY_CANCELED_CODE.equals(body.path("code").asString());
		} catch (Exception parseError) {
			return false;
		}
	}

	/** paymentKey 는 그 값만으로 confirm·cancel 을 호출할 수 있는 자격증명이라 로그에 통째로 남기지 않음 */
	private String mask(String paymentKey) {
		if (paymentKey == null || paymentKey.length() <= 8) {
			return "***";
		}
		return paymentKey.substring(0, 4) + "..." + paymentKey.substring(paymentKey.length() - 4);
	}
}
