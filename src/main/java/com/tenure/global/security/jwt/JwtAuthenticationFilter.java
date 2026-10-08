package com.tenure.global.security.jwt;

import com.tenure.global.security.AuthUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer <access token>} 을 읽어 SecurityContext 를 채운다.
 *
 * <p>토큰이 없거나 깨졌으면 컨텍스트를 비운 채 그냥 넘긴다 — 401 을 낼지는
 * 인가 규칙(permitAll 인지 authenticated 인지)이 정하지 이 필터가 정하지 않는다.
 *
 * <p><b>빈으로 등록하지 않는다.</b> {@code Filter} 타입 빈은 Spring Boot 가 서블릿 필터
 * 체인에도 자동 등록해서, 시큐리티 체인 안({@code addFilterBefore})과 밖에 이중으로 걸린다.
 * 이 필터는 시큐리티 체인의 구성요소이지 범용 서블릿 필터가 아니므로
 * {@link com.tenure.global.security.SecurityConfig} 가 직접 생성해 넣는다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String HEADER = "Authorization";
	private static final String PREFIX = "Bearer ";

	private final JwtTokenProvider tokenProvider;
	private final WithdrawnUserStore withdrawnUserStore;

	public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, WithdrawnUserStore withdrawnUserStore) {
		this.tokenProvider = tokenProvider;
		this.withdrawnUserStore = withdrawnUserStore;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String token = resolveToken(request);
		if (token != null) {
			Claims claims = tokenProvider.parseOrNull(token);
			// refresh token 은 인증에 쓰지 않는다. tid 클레임이 있으면 refresh 다
			if (claims != null && tokenProvider.getTokenId(claims) == null) {
				Long userId = tokenProvider.getUserId(claims);
				// 탈퇴 직후에도 서명·만료는 멀쩡해 토큰 자체로는 걸러지지 않는다 — 탈퇴 시점에
				// 남겨 둔 Redis 표시로 자연 만료 전까지의 구멍을 막는다
				if (!withdrawnUserStore.isWithdrawn(userId)) {
					AuthUser principal = new AuthUser(userId);
					SecurityContextHolder.getContext().setAuthentication(
							new UsernamePasswordAuthenticationToken(principal, null, List.of()));
				}
			}
		}
		filterChain.doFilter(request, response);
	}

	private String resolveToken(HttpServletRequest request) {
		String header = request.getHeader(HEADER);
		return StringUtils.hasText(header) && header.startsWith(PREFIX)
				? header.substring(PREFIX.length())
				: null;
	}
}
