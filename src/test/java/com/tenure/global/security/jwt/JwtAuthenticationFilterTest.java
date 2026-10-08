package com.tenure.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.tenure.global.security.AuthUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 탈퇴 여부 확인을 뺀 나머지(서명 실패·refresh token)는 StompAuthChannelInterceptorTest 와
 * 판정 기준이 같아 여기서는 중복하지 않고, 이번에 새로 들어간 분기(WithdrawnUserStore)만 본다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

	private static final Long USER_ID = 1L;
	private static final String TOKEN = "access-token";

	@Mock private JwtTokenProvider tokenProvider;
	@Mock private WithdrawnUserStore withdrawnUserStore;
	@Mock private FilterChain filterChain;
	@Mock private Claims claims;

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("탈퇴하지 않은 사용자의 access token 이면 인증 컨텍스트를 채운다")
	void notWithdrawn_authenticatesRequest() throws Exception {
		given(tokenProvider.parseOrNull(TOKEN)).willReturn(claims);
		given(tokenProvider.getTokenId(any())).willReturn(null);
		given(tokenProvider.getUserId(any())).willReturn(USER_ID);
		given(withdrawnUserStore.isWithdrawn(USER_ID)).willReturn(false);

		runFilter();

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		assertThat(auth).isInstanceOf(UsernamePasswordAuthenticationToken.class);
		assertThat(auth.getPrincipal()).isEqualTo(new AuthUser(USER_ID));
		verify(filterChain).doFilter(any(), any());
	}

	@Test
	@DisplayName("탈퇴한 사용자면 서명·만료가 멀쩡해도 인증 컨텍스트를 채우지 않는다")
	void withdrawn_skipsAuthentication() throws Exception {
		given(tokenProvider.parseOrNull(TOKEN)).willReturn(claims);
		given(tokenProvider.getTokenId(any())).willReturn(null);
		given(tokenProvider.getUserId(any())).willReturn(USER_ID);
		given(withdrawnUserStore.isWithdrawn(USER_ID)).willReturn(true);

		runFilter();

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		// 인증만 비우고 체인은 그대로 넘긴다 — 401 여부는 인가 규칙이 정한다
		verify(filterChain).doFilter(any(), any());
	}

	@Test
	@DisplayName("refresh token(tid 클레임 있음)은 탈퇴 여부를 보지 않고 애초에 인증에 쓰지 않는다")
	void refreshToken_neverChecksWithdrawnStatus() throws Exception {
		given(tokenProvider.parseOrNull(TOKEN)).willReturn(claims);
		given(tokenProvider.getTokenId(any())).willReturn("some-tid");

		runFilter();

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(withdrawnUserStore, never()).isWithdrawn(any());
	}

	private void runFilter() throws Exception {
		JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenProvider, withdrawnUserStore);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + TOKEN);
		filter.doFilter(request, new MockHttpServletResponse(), filterChain);
	}
}
