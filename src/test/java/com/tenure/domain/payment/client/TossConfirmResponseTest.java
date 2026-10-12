package com.tenure.domain.payment.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.payment.client.TossConfirmResponse.EasyPay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code displayMethod()} 는 간편결제(easyPay)가 있을 때만 표시 문자열을 돌려준다 — 카드 결제처럼
 * easyPay 가 내려오지 않는 경우를 null 로 처리하는지가 핵심.
 */
@DisplayName("TossConfirmResponse")
class TossConfirmResponseTest {

	@Test
	@DisplayName("easyPay 가 없으면 표시 문자열도 null 이다")
	void displayMethod_noEasyPay_returnsNull() {
		TossConfirmResponse response = new TossConfirmResponse("key", "order", 100_000, null);

		assertThat(response.displayMethod()).isNull();
	}

	@Test
	@DisplayName("easyPay 가 있으면 provider 를 그대로 돌려준다")
	void displayMethod_withEasyPay_returnsProvider() {
		TossConfirmResponse response = new TossConfirmResponse("key", "order", 100_000, new EasyPay("토스페이"));

		assertThat(response.displayMethod()).isEqualTo("토스페이");
	}
}
