package com.tenure.domain.offer.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 결제 금액 미리보기. 제안을 보내기 전 금액 4행을 확인하는 화면이 쓴다 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "결제 금액 미리보기")
public class OfferPreviewResponse {

	@Schema(description = "상품 금액", example = "360000")
	private final String itemPrice;

	@Schema(description = "배송비", example = "5000")
	private final String shippingFee;

	@Schema(description = "요율. 소유자 등급 기준 0.06 또는 0.03", example = "0.06")
	private final BigDecimal feeRate;

	@Schema(description = "총 수수료", example = "21600")
	private final String feeAmount;

	@Schema(description = "구매자 부담 수수료", example = "21600")
	private final String buyerFeeAmount;

	@Schema(description = "판매자 부담 수수료", example = "0")
	private final String sellerFeeAmount;

	@Schema(description = "총 결제 금액", example = "386600")
	private final String totalPaymentAmount;

	@Schema(description = "총 정산 예정 금액", example = "365000")
	private final String expectedPayoutAmount;
}
