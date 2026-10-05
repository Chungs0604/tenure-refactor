package com.tenure.global.holiday;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 외부 API(공공데이터포털) 호출용 RestClient.Builder.
 *
 * RestClientAutoConfiguration 이 이 프로젝트에선 활성화되지 않아 직접 빈으로 둔다 —
 * HolidayApiClient 가 매번 build() 해서 요청별로 독립된 RestClient 를 쓴다.
 *
 * 타임아웃을 반드시 건다 — settleDue() 가 거래 행을 잠그기 전에 이 호출을 먼저 끝내도록
 * 옮겨뒀지만, 그래도 응답이 없는 외부 API 가 배치 전체의 트랜잭션을 무한정 붙잡는 일은
 * 막아야 한다
 */
@Configuration
public class RestClientConfig {

	private static final int CONNECT_TIMEOUT_MS = 3_000;
	private static final int READ_TIMEOUT_MS = 5_000;

	@Bean
	public RestClient.Builder restClientBuilder() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
		factory.setReadTimeout(READ_TIMEOUT_MS);
		return RestClient.builder().requestFactory(factory);
	}
}
