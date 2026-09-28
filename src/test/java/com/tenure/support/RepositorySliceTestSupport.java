package com.tenure.support;

import com.tenure.global.config.JpaAuditingConfig;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Repository 슬라이스 테스트의 공통 조상.
 *
 * <p>내장 DB 로 바꾸지 않는다({@code replace = NONE}) — H2 는 collation·정렬 순서가 달라
 * 여기서 통과하고 운영에서 깨진다. {@link IntegrationTestSupport} 가 띄운 컨테이너를
 * 그대로 쓰므로 컨테이너가 하나 더 뜨지 않는다.
 *
 * <p>{@code @DataJpaTest} 는 테스트마다 트랜잭션을 열고 롤백하므로 데이터를 직접 지울 필요가 없다.
 *
 * <p>{@link JpaAuditingConfig} 를 명시적으로 가져온다 — 이 슬라이스는 {@code @Configuration} 을
 * 스캔하지 않아 {@code created_at}·{@code updated_at} 이 비고 NOT NULL 로 막힌다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class RepositorySliceTestSupport {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", IntegrationTestSupport.MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", IntegrationTestSupport.MYSQL::getUsername);
		registry.add("spring.datasource.password", IntegrationTestSupport.MYSQL::getPassword);
	}
}
