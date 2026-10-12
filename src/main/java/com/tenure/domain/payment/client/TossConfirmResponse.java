package com.tenure.domain.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 토스페이먼츠 결제 승인(confirm) 응답. 필요한 필드만 받는다 — 나머지는 무시한다 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossConfirmResponse(String paymentKey, String orderId, Integer totalAmount, EasyPay easyPay) {

	/** 간편결제(토스페이·카카오페이·네이버페이)로 결제했을 때만 내려온다 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record EasyPay(String provider) {
	}

	/** 간편결제 제공사명('토스페이'·'카카오페이' 등). easyPay 가 없으면(카드 등) null */
	public String displayMethod() {
		return easyPay != null ? easyPay.provider() : null;
	}
}
