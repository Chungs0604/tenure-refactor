package com.tenure.support;

import com.tenure.global.config.JpaAuditingConfig;
import com.tenure.global.config.QuerydslConfig;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Repository 슬라이스 테스트의 공통 조상.
 *
 * 내장 DB 로 바꾸지 않는다(replace = NONE) — H2 는 collation·정렬 순서가 달라
 * 여기서 통과하고 운영에서 깨진다. IntegrationTestSupport 가 띄운 컨테이너를
 * 그대로 쓰므로 컨테이너가 하나 더 뜨지 않는다.
 *
 * @DataJpaTest 는 테스트마다 트랜잭션을 열고 롤백하므로 데이터를 직접 지울 필요가 없다.
 *
 * JpaAuditingConfig 를 명시적으로 가져온다 — 이 슬라이스는 @Configuration 을
 * 스캔하지 않아 created_at·updated_at 이 비고 NOT NULL 로 막힌다.
 *
 * QuerydslConfig 도 같은 이유로 가져온다. 슬라이스는 리포지토리를 전부 올리므로
 * QueryDSL 구현체(예: PostRepositoryImpl)가 있으면 JPAQueryFactory 빈이 없어
 * 그 도메인과 무관한 테스트까지 컨텍스트 로딩 단계에서 깨진다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class RepositorySliceTestSupport {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", IntegrationTestSupport.MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", IntegrationTestSupport.MYSQL::getUsername);
		registry.add("spring.datasource.password", IntegrationTestSupport.MYSQL::getPassword);
	}
}
