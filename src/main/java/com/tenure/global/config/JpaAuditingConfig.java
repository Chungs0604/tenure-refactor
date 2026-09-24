package com.tenure.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * {@code @CreatedDate}·{@code @LastModifiedDate} 활성화.
 *
 * <p>별도 클래스로 둔다 — 메인 클래스에 달면 {@code @WebMvcTest} 같은 슬라이스 테스트가
 * 아우디팅 빈을 찾지 못해 깨진다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
