package com.tenure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@ConfigurationPropertiesScan
@EnableScheduling
// 인증은 JWT 클레임만으로 끝난다(AuthUser). UserDetailsService 를 두지 않으므로
// 자동 구성이 만드는 인메모리 사용자와 기동 때마다 찍히는 임시 비밀번호를 끈다.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class TenureApplication {

	public static void main(String[] args) {
		SpringApplication.run(TenureApplication.class, args);
	}

}
