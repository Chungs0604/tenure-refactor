package com.tenure.global.security;

import com.tenure.global.security.jwt.JwtAuthenticationFilter;
import com.tenure.global.security.jwt.JwtTokenProvider;
import com.tenure.global.security.jwt.WithdrawnUserStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 무상태 JWT 인증.
 *
 * <p>인증이 필요 없는 경로는 로그인·회원가입·비밀번호 찾기뿐이다(화면 47개 중 3개).
 * 나머지는 전부 인증을 요구하므로 {@code anyRequest().authenticated()} 가 기본이다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	/** 인증 없이 열어두는 경로. docs/api-spec.md 의 `인증 X` 엔드포인트와 1:1 대응한다. */
	private static final String[] PUBLIC_POST = {
			"/api/v1/auth/login",                          // API-001
			"/api/v1/auth/reissue",                        // API-003
			"/api/v1/auth/email-verifications",            // API-004
			"/api/v1/auth/email-verifications/verify",     // API-005
			"/api/v1/users"                                // API-012 회원가입
	};

	private static final String[] PUBLIC_GET = {
			"/api/v1/users/check-email",                   // API-010
			"/api/v1/users/check-nickname"                 // API-011
	};

	private static final String[] PUBLIC_DOCS = {
			"/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
			"/uploads/**"                                  // 로컬 저장소 정적 노출 (DEC-006)
	};

	/** 채팅 WS 핸드셰이크. 실제 인증 경계는 STOMP CONNECT 프레임이다(StompAuthChannelInterceptor) */
	private static final String[] PUBLIC_WS = {
			"/ws-chat/**"
	};

	private final JwtTokenProvider jwtTokenProvider;
	private final WithdrawnUserStore withdrawnUserStore;
	private final JsonAuthEntryPoint jsonAuthEntryPoint;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http
				// 무상태 + Bearer 토큰이라 CSRF 토큰을 쓸 수 없고 필요도 없다
				.csrf(csrf -> csrf.disable())
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(PUBLIC_DOCS).permitAll()
						.requestMatchers(PUBLIC_WS).permitAll()
						.requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
						.requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
						.requestMatchers(HttpMethod.PATCH, "/api/v1/auth/password").permitAll()  // API-006
						.anyRequest().authenticated())
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(jsonAuthEntryPoint)
						.accessDeniedHandler(jsonAuthEntryPoint))
				.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, withdrawnUserStore),
						UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
