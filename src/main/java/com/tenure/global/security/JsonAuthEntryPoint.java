package com.tenure.global.security;

import tools.jackson.databind.ObjectMapper;
import com.tenure.global.exception.CommonErrorCode;
import com.tenure.global.exception.ErrorCode;
import com.tenure.global.response.BaseResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 필터 단계에서 끊긴 요청도 {@link BaseResponse} 로 내려준다.
 *
 * <p>이걸 두지 않으면 인증 실패만 스프링 기본 HTML 오류 페이지가 나가서
 * 클라이언트가 응답 형식을 두 가지로 처리해야 한다.
 *
 * <p>{@code ObjectMapper} 는 <b>Jackson 3</b>({@code tools.jackson}) 이다 — Spring Boot 4 가
 * 자동 구성하는 것이 이쪽이고, 클래스패스의 {@code com.fasterxml.jackson} 2.x 는
 * jjwt·AWS SDK 가 끌고 온 것이라 빈으로 등록되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class JsonAuthEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final ObjectMapper objectMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		write(response, CommonErrorCode.UNAUTHORIZED);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(response, CommonErrorCode.FORBIDDEN);
	}

	private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(), BaseResponse.fail(errorCode));
	}
}
