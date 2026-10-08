package com.tenure.support;

import com.tenure.global.security.JsonAuthEntryPoint;
import com.tenure.global.security.SecurityConfig;
import com.tenure.global.security.jwt.JwtTokenProvider;
import com.tenure.global.security.jwt.WithdrawnUserStore;
import com.tenure.global.storage.StorageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

/**
 * Controller 슬라이스 테스트의 공통 조상.
 *
 * <p>{@code @WebMvcTest} 는 {@code @Configuration} 을 스캔하지 않으므로 {@link SecurityConfig} 를
 * 명시적으로 가져온다. 가져오지 않으면 Spring Security 기본 체인이 적용되어 <b>permitAll 경로가
 * 401 로 막히고</b>, 그러면 이 테스트들이 검증하려는 대상(공개 경로·응답 형식)이 사라진다.
 *
 * <p>{@link JsonAuthEntryPoint} 도 실물을 가져온다 — 필터 단계에서 끊긴 요청이
 * HTML 이 아니라 {@code BaseResponse} JSON 으로 나가는지가 검증 대상이기 때문이다.
 *
 * <p>{@link JwtTokenProvider} 만 mock 이다. 서명 키가 필요해서가 아니라, 이 슬라이스에서
 * 검증할 것이 <b>토큰을 안 들고 왔을 때</b>의 동작이라 실제 검증 로직이 필요 없다.
 *
 * <p>{@link WithdrawnUserStore} 도 Redis 가 없는 슬라이스라 mock — SecurityConfig 가
 * JwtAuthenticationFilter 생성에 그대로 쓴다. stub 하지 않으면 전부 false(탈퇴 아님)라
 * 기존 테스트의 인증 흐름에 영향이 없다.
 *
 * <p>{@link StorageProperties} 를 켜주는 이유 — {@code @WebMvcTest} 는 {@code WebMvcConfigurer} 를
 * 포함하므로 {@code LocalStorageWebConfig}(업로드 경로 정적 노출, DEC-006)가 함께 올라오는데,
 * 그것이 의존하는 {@code @ConfigurationProperties} 는 스캔 대상이 아니라 따로 등록해야 한다.
 */
@Import({SecurityConfig.class, JsonAuthEntryPoint.class})
@EnableConfigurationProperties(StorageProperties.class)
public abstract class ControllerSliceTestSupport {

	@Autowired protected MockMvc mvc;
	@Autowired protected ObjectMapper objectMapper;

	@MockitoBean protected JwtTokenProvider jwtTokenProvider;
	@MockitoBean protected WithdrawnUserStore withdrawnUserStore;

	/** 요청 본문 직렬화. Jackson 3 컨테이너 빈을 그대로 쓴다 — 테스트가 별도 매퍼를 만들면 설정이 갈린다. */
	protected String json(Object body) {
		return objectMapper.writeValueAsString(body);
	}
}
