package com.tenure.support;

import com.tenure.domain.payment.client.ConfirmOutcome;
import com.tenure.domain.payment.client.TossConfirmResponse;
import com.tenure.domain.payment.client.TossPaymentClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 스프링 컨텍스트를 실제로 띄우는 테스트의 공통 조상.
 *
 * <p>H2 를 쓰지 않는다 — collation·날짜 함수·정렬 순서가 달라 H2 에서 통과하고 운영에서 깨진다.
 * 컨테이너는 {@code static} + {@code withReuse(true)} 라 클래스가 늘어도 한 번만 뜬다.
 * 재사용을 켜려면 {@code ~/.testcontainers.properties} 에
 * {@code testcontainers.reuse.enable=true} 를 둔다.
 */
@Testcontainers
@SpringBootTest
@Import(IntegrationTestSupport.StubTossPaymentConfig.class)
public abstract class IntegrationTestSupport {

	/**
	 * 토스페이먼츠 실 API 를 대체함. 통합 테스트는 결제위젯을 거친 적 없는 더미
	 * paymentKey·orderId 를 쓰므로, 진짜 TossPaymentClient 를 그대로 두면 모든 제안 생성
	 * 테스트가 PAYMENT_402 로 깨진다 — 여기서 검증할 건 결제 게이트웨이 연동이 아니라
	 * 동시성·소유권·상태 전이 로직이라 항상 성공하는 가짜로 대체한다. 실제 연동 자체는
	 * TossPaymentClient 를 네트워크로 호출하는 별도 테스트(있다면)가 맡는다.
	 *
	 * 빈 이름을 컴포넌트 스캔 이름({@code tossPaymentClient})과 다르게 둔다 — 같으면
	 * {@code @Bean} 정의가 스캔 정의를 완전히 대체해버려, 진짜 {@code TossPaymentClient}
	 * 빈이 어떤 테스트에서도 생성되지 않는다. 그러면 그 빈의 생성자 배선(타입 불일치 ·
	 * {@code @Qualifier} 오타 등)이 깨져 있어도 테스트가 절대 못 잡는다 — 실제로 한 번
	 * 그렇게 숨은 적이 있다. 이름을 다르게 두고 {@code @Primary} 로만 우선권을 주면 진짜
	 * 빈도 함께 생성돼 그 배선이 매번 검증된다
	 */
	@TestConfiguration
	static class StubTossPaymentConfig {

		@Bean
		@Primary
		TossPaymentClient stubTossPaymentClient() {
			TossPaymentClient stub = Mockito.mock(TossPaymentClient.class);
			given(stub.confirm(any(), any(), any())).willAnswer(invocation -> new ConfirmOutcome.Success(
					new TossConfirmResponse(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), null)));
			given(stub.cancel(any(), any())).willReturn(true);
			return stub;
		}
	}

	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
			.withDatabaseName("tenure")
			.withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci")
			.withReuse(true);

	static {
		MYSQL.start();
	}

	/**
	 * 비밀값은 {@code .env} 로 분리돼 있어 {@code application.yaml} 에 기본값이 없다.
	 * 테스트는 실제 비밀이 필요 없으므로 여기서 더미를 주입한다 — 테스트가 개발자
	 * 로컬 환경 파일에 의존하면 CI 에서 재현되지 않는다.
	 */
	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);

		// HS256 최소 32바이트. 테스트 전용 더미이며 어디에도 쓰이지 않는다
		registry.add("app.jwt.secret", () -> "test-only-jwt-secret-key-32bytes-min!");
		registry.add("spring.data.redis.password", () -> "");
		registry.add("spring.mail.password", () -> "");
		// 실제로 호출되지 않는다 — StubTossPaymentConfig 가 TossPaymentClient 자체를 대체하지만,
		// 컴포넌트 스캔으로 진짜 빈도 함께 만들어져 TossPaymentProperties 바인딩은 여전히 필요하다
		registry.add("app.toss.secret-key", () -> "test-only-toss-secret-key");
	}
}
