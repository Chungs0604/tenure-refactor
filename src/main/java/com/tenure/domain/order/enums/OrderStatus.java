package com.tenure.domain.order.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 거래 상세 '거래 진행 단계' 설명 문구에서 순서 확정 + 타임라인 체크 5단계(25.05.26 결제완료 → 25.05.28 상품발송 → 25.06.01 배송완료 → 25.06.02 구매…
 */
public enum OrderStatus {

	/** 결제 완료 */
	PAYMENT_COMPLETED,
	/** 상품 발송 */
	SHIPPED,
	/** 배송 완료 */
	DELIVERED,
	/** 구매 확정 */
	PURCHASE_CONFIRMED,
	/**
	 * 정산 완료. 최종 상태 — 별도의 COMPLETED 는 없다. 표시 라벨만 viewerRole 에 따라
	 * '정산 완료'(SELLER)/'거래 완료'(BUYER)로 갈린다
	 */
	SETTLED,
	/** 거래 취소 */
	CANCELED;

	/** 끝난 거래. 이 둘만 아니면 진행 중으로 봄 — 중간에 상태가 늘어도 자동으로 포함됨 */
	public static final Set<OrderStatus> FINISHED =
			Collections.unmodifiableSet(EnumSet.of(SETTLED, CANCELED));

}
