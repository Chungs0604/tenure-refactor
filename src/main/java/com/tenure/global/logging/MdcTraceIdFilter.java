package com.tenure.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 하나에 짧은 추적 ID 를 붙인다. 로그 패턴의 {@code %X{traceId}} 가 이 값을 읽는다.
 *
 * <p>{@code MDC.clear()} 를 finally 에 두지 않으면 스레드 풀에서 값이 다음 요청으로 샌다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcTraceIdFilter extends OncePerRequestFilter {

	private static final String TRACE_ID = "traceId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		MDC.put(TRACE_ID, UUID.randomUUID().toString().substring(0, 8));
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.clear();
		}
	}
}
