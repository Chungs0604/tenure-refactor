package com.tenure.domain.payment.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 토스페이먼츠 결제 승인(confirm)·취소(cancel) 연동 설정. 시크릿 키는 .env 로 분리한다. */
@ConfigurationProperties(prefix = "app.toss")
public record TossPaymentProperties(String baseUrl, String secretKey) {
}
