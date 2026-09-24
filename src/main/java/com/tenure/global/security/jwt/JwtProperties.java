package com.tenure.global.security.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정.
 *
 * @param secret               HS256 서명 키. 최소 32바이트여야 한다
 * @param accessExpiration     access token 수명
 * @param refreshExpiration    refresh token 기본 수명
 * @param refreshExpirationKeepLoggedIn `로그인 상태 유지` 를 켰을 때의 refresh 수명 (API-001)
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
		String secret,
		Duration accessExpiration,
		Duration refreshExpiration,
		Duration refreshExpirationKeepLoggedIn) {
}
