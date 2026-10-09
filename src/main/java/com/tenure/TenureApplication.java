package com.tenure;

import java.util.TimeZone;
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
		// order·스케줄러는 Asia/Seoul 을 명시 고정하는데 나머지 도메인·JPA auditing 은
		// JVM 기본 시간대를 쓴다. 서버가 UTC 로 뜨면 한 거래의 타임라인에 두 시계가 섞인다 —
		// 기본 시간대 자체를 고정해 전부 같은 시계를 쓰게 한다.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(TenureApplication.class, args);
	}

}
