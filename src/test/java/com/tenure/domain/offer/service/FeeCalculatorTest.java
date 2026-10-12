package com.tenure.domain.offer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.user.enums.UserGrade;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FeeCalculator")
class FeeCalculatorTest {

	private static final BigDecimal RECORD_RATE = FeeCalculator.rateOf(UserGrade.RECORD);

	@Test
	@DisplayName("판매자 부담이면 수수료 전액을 판매자가 내고 배송비는 그대로 더해진다")
	void calculate_sellerBearer_sellerPaysWholeFee() {
		FeeCalculator.Result result = FeeCalculator.calculate(100_000, 5_000, RECORD_RATE, FeeBearer.SELLER);

		assertThat(result.getFeeAmount()).isEqualTo(3_000);
		assertThat(result.getBuyerFeeAmount()).isEqualTo(0);
		assertThat(result.getSellerFeeAmount()).isEqualTo(3_000);
		assertThat(result.getTotalPaymentAmount()).isEqualTo(105_000);
		assertThat(result.getExpectedPayoutAmount()).isEqualTo(102_000);
	}

	@Test
	@DisplayName("구매자 부담이면 수수료가 결제 금액에 얹히고 정산액은 상품가+배송비 그대로다")
	void calculate_buyerBearer_buyerPaysWholeFee() {
		FeeCalculator.Result result = FeeCalculator.calculate(100_000, 5_000, RECORD_RATE, FeeBearer.BUYER);

		assertThat(result.getBuyerFeeAmount()).isEqualTo(3_000);
		assertThat(result.getSellerFeeAmount()).isEqualTo(0);
		assertThat(result.getTotalPaymentAmount()).isEqualTo(108_000);
		assertThat(result.getExpectedPayoutAmount()).isEqualTo(105_000);
	}

	@Test
	@DisplayName("반반 부담이면 홀수 1원은 판매자가 더 낸다")
	void calculate_splitBearer_sellerPaysOddRemainder() {
		// feeAmount = floor(100 * 0.03) = 3 (홀수)
		FeeCalculator.Result result = FeeCalculator.calculate(100, 0, RECORD_RATE, FeeBearer.SPLIT);

		assertThat(result.getFeeAmount()).isEqualTo(3);
		assertThat(result.getBuyerFeeAmount()).isEqualTo(1);
		assertThat(result.getSellerFeeAmount()).isEqualTo(2);
	}

	@Test
	@DisplayName("배송비가 상한을 뚫고 매우 큰 값으로 들어오면 int 합산이 조용히 음수로 오버플로하는 대신 즉시 예외를 던진다")
	void calculate_hugeShippingFee_throwsInsteadOfOverflowing() {
		// RECORD 등급이 보낼 수 있는 극단값 — @Max 입력 검증이 뚫렸다고 가정한 방어선 테스트
		int itemPrice = 100_000_000;
		int hugeShippingFee = 2_100_000_000;

		assertThatThrownBy(() ->
				FeeCalculator.calculate(itemPrice, hugeShippingFee, RECORD_RATE, FeeBearer.SELLER))
				.isInstanceOf(ArithmeticException.class);
	}
}
