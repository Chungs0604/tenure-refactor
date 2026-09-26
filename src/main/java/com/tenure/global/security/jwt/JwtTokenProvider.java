package com.tenure.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * access / refresh 토큰 발급·검증.
 *
 * <p>refresh token 은 서명만으로 유효성을 판단하지 않는다 — 로그아웃 무효화가 불가능해지기
 * 때문이다. 저장소(Redis)에 {@code tokenId} 가 살아 있는지 함께 확인한다(DEC-010).
 */
@Slf4j
@Component
public class JwtTokenProvider {

	private static final String CLAIM_TOKEN_ID = "tid";

	private final SecretKey key;
	private final JwtProperties properties;

	public JwtTokenProvider(JwtProperties properties) {
		String configured = properties.secret();
		// 미해석 placeholder 가 그대로 넘어오면 "길이 부족" 으로만 보여 원인이 가려진다
		if (configured == null || configured.startsWith("${")) {
			throw new IllegalStateException(
					"JWT_SECRET 환경변수가 주입되지 않았다. .env.example 을 복사해 .env 를 만들고 값을 채운다");
		}
		byte[] secret = configured.getBytes(StandardCharsets.UTF_8);
		if (secret.length < 32) {
			throw new IllegalStateException("app.jwt.secret 은 최소 32바이트여야 한다 (현재 " + secret.length + ")");
		}
		this.key = Keys.hmacShaKeyFor(secret);
		this.properties = properties;
	}

	public String issueAccessToken(Long userId) {
		return build(userId, properties.accessExpiration(), null);
	}

	/**
	 * refresh token 을 발급한다. 함께 돌려주는 {@code tokenId} 를 저장소 키에 쓴다.
	 *
	 * @param keepLoggedIn `로그인 상태 유지` 여부. 켜면 수명이 길어진다
	 */
	public IssuedRefreshToken issueRefreshToken(Long userId, boolean keepLoggedIn) {
		String tokenId = UUID.randomUUID().toString();
		Duration ttl = keepLoggedIn
				? properties.refreshExpirationKeepLoggedIn()
				: properties.refreshExpiration();
		return new IssuedRefreshToken(build(userId, ttl, tokenId), tokenId, ttl);
	}

	/** 서명·만료가 유효하면 클레임을, 아니면 {@code null} 을 돌려준다. */
	public Claims parseOrNull(String token) {
		try {
			return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
		} catch (JwtException | IllegalArgumentException e) {
			log.debug("토큰 파싱 실패: {}", e.getMessage());
			return null;
		}
	}

	public Long getUserId(Claims claims) {
		return Long.valueOf(claims.getSubject());
	}

	public String getTokenId(Claims claims) {
		return claims.get(CLAIM_TOKEN_ID, String.class);
	}

	private String build(Long userId, Duration ttl, String tokenId) {
		long now = System.currentTimeMillis();
		var builder = Jwts.builder()
				.subject(String.valueOf(userId))
				.issuedAt(new Date(now))
				.expiration(new Date(now + ttl.toMillis()));
		if (tokenId != null) {
			builder.claim(CLAIM_TOKEN_ID, tokenId);
		}
		return builder.signWith(key).compact();
	}

	/** 발급된 refresh token 과, 저장소에 넣을 식별자·수명. */
	public record IssuedRefreshToken(
			String token,
			String tokenId,
			Duration ttl
	) {}
}
