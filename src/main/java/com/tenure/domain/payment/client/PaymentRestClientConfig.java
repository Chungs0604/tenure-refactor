package com.tenure.domain.payment.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 토스페이먼츠 호출용 RestClient.Builder. 공용 빈(공공데이터포털 기준 5초)을 그대로 쓰지 않는다 —
 * 간편결제 confirm 은 PG·카드사 왕복이 끼어 5초를 넘기는 일이 드물지 않은데, 그걸 넘기면
 * {@code TossPaymentClient} 가 "응답을 못 받음" 을 "확정 실패" 로 잘못 단정하게 된다. 읽기
 * 타임아웃을 넉넉히 둬서 그 오판을 줄인다 — 타임아웃을 아예 없애진 않는다, 응답이 없는 외부
 * API 가 트랜잭션을 무한정 붙잡으면 안 되기 때문이다.
 */
@Configuration
public class PaymentRestClientConfig {

	private static final int CONNECT_TIMEOUT_MS = 3_000;
	private static final int READ_TIMEOUT_MS = 15_000;

	@Bean
	public RestClient.Builder paymentRestClientBuilder() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
		factory.setReadTimeout(READ_TIMEOUT_MS);
		return RestClient.builder().requestFactory(factory);
	}
}
