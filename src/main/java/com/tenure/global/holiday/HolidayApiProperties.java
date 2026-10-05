package com.tenure.global.holiday;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 공공데이터포털 특일 정보 서비스(getRestDeInfo) 연동 설정. 서비스 키는 .env 로 분리한다. */
@ConfigurationProperties(prefix = "app.holiday")
public record HolidayApiProperties(String baseUrl, String serviceKey) {
}
