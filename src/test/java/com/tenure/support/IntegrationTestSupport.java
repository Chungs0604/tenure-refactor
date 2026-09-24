package com.tenure.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

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
public abstract class IntegrationTestSupport {

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
	}
}
