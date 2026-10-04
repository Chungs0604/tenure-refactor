package com.tenure.domain.offer.service;

import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.user.enums.UserGrade;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구매 제안 금액 계산. 미리보기와 생성 전송이 같은 계산을 쓰며, 수락 시 생성되는 거래는 이 결과를
 * 다시 계산하지 않고 제안의 스냅샷을 그대로 옮겨 씀
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FeeCalculator {

	private static final BigDecimal BASIC_RATE = new BigDecimal("0.06");
	private static final BigDecimal RECORD_RATE = new BigDecimal("0.03");

	/** 소유자 등급 기준 요율. BASIC 6% / RECORD 3% */
	public static BigDecimal rateOf(UserGrade grade) {
		return grade.isBasic() ? BASIC_RATE : RECORD_RATE;
	}

	/**
	 * 상품 금액 · 배송비 · 수수료 부담으로 최종 금액 넷을 계산함.
	 *
	 * 수수료는 상품 금액에만 붙고 배송비에는 붙지 않음. 배송비는 부담 설정과 무관하게 항상 구매자가
	 * 내고 판매자에게 그대로 전달됨 — 택배비를 실제로 치르는 쪽이 판매자라서임
	 */
	public static Result calculate(int itemPrice, int shippingFee, BigDecimal feeRate, FeeBearer feeBearer) {
		// 수수료는 상품 금액에만 붙고, 소수점 이하는 버림
		int feeAmount = BigDecimal.valueOf(itemPrice)
				.multiply(feeRate)
				.setScale(0, RoundingMode.FLOOR)
				.intValue();

		// 부담 설정에 따라 총수수료를 구매자·판매자로 나눔
		int buyerFeeAmount;
		int sellerFeeAmount;
		switch (feeBearer) {
			case SELLER -> {
				buyerFeeAmount = 0;
				sellerFeeAmount = feeAmount;
			}
			case BUYER -> {
				buyerFeeAmount = feeAmount;
				sellerFeeAmount = 0;
			}
			case SPLIT -> {
				// 반반 나누고 홀수 1원은 판매자가 더 냄
				buyerFeeAmount = feeAmount / 2;
				sellerFeeAmount = feeAmount - buyerFeeAmount;
			}
			default -> throw new IllegalStateException("알 수 없는 수수료 부담임: " + feeBearer);
		}

		// 배송비는 부담과 무관하게 항상 구매자가 내고 판매자에게 그대로 전달됨
		int totalPaymentAmount = itemPrice + shippingFee + buyerFeeAmount;
		int expectedPayoutAmount = itemPrice + shippingFee - sellerFeeAmount;

		return new Result(feeAmount, buyerFeeAmount, sellerFeeAmount, totalPaymentAmount, expectedPayoutAmount);
	}

	@Getter
	public static final class Result {
		private final int feeAmount;
		private final int buyerFeeAmount;
		private final int sellerFeeAmount;
		private final int totalPaymentAmount;
		private final int expectedPayoutAmount;

		private Result(
				int feeAmount, int buyerFeeAmount, int sellerFeeAmount,
				int totalPaymentAmount, int expectedPayoutAmount
		) {
			this.feeAmount = feeAmount;
			this.buyerFeeAmount = buyerFeeAmount;
			this.sellerFeeAmount = sellerFeeAmount;
			this.totalPaymentAmount = totalPaymentAmount;
			this.expectedPayoutAmount = expectedPayoutAmount;
		}
	}
}
