package com.tenure.domain.payment.client;

/**
 * 결제 승인(confirm) 호출 결과. Unknown 은 '실패'가 아니라 '확인되지 않음' — 타임아웃·5xx처럼
 * 토스가 명시적으로 거부하지 않았는데 응답을 받지 못한 경우다. Rejected(토스가 명시적으로
 * 거부함)와 다르게 취급해야 한다 — 응답을 못 받았다고 안 됐다고 단정하면, 실제로는 승인됐는데
 * 우리만 실패로 처리해 돈만 빠지는 상태가 될 수 있다.
 */
public sealed interface ConfirmOutcome {

	record Success(TossConfirmResponse response) implements ConfirmOutcome {
	}

	record Rejected() implements ConfirmOutcome {
	}

	record Unknown() implements ConfirmOutcome {
	}
}
