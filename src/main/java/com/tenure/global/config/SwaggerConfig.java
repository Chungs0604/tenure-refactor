package com.tenure.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** springdoc 공통 설정. 문서는 {도메인}Api 인터페이스에 달고 Controller 는 구현만 한다. */
@Configuration
public class SwaggerConfig {

	private static final String SECURITY_SCHEME = "bearerAuth";

	@Bean
	public OpenAPI openAPI() {
		SecurityScheme bearer = new SecurityScheme()
				.type(SecurityScheme.Type.HTTP)
				.scheme("bearer")
				.bearerFormat("JWT");

		return new OpenAPI()
				.components(new Components().addSecuritySchemes(SECURITY_SCHEME, bearer))
				.addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
				.info(new Info()
						.title("Tenure API")
						.version("v1")
						.description("OOTD 착용 기록으로 옷의 가치를 증명하는 패션 C2C 거래 서비스"));
	}
}
