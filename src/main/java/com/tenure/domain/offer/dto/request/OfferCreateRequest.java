package com.tenure.domain.offer.dto.request;

import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.payment.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구매 제안 / 거래 의사 전송 요청. offerPrice 는 미판매 구매 제안에서만 받음 — 판매 중 거래
 * 의사는 이미 걸린 판매가를 그대로 씀
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "구매 제안 / 거래 의사 전송 요청")
public class OfferCreateRequest {

	@Schema(description = "구매 제안(미판매) / 거래 의사(판매 중)", example = "PURCHASE_OFFER")
	@NotNull
	private OfferType offerType;

	@Schema(description = "제안 금액. 미판매 구매 제안일 때만 받는다", example = "360000")
	@Min(1)
	@Max(100_000_000)
	private Integer offerPrice;

	@Schema(description = "배송지 id", example = "1")
	@NotNull
	private Long shippingAddressId;

	@Schema(description = "거래 요청사항", example = "직거래도 가능할까요?")
	@Size(max = 500)
	private String buyerRequest;

	@Schema(description = "결제 수단", example = "TOSS_PAY")
	@NotNull
	private PaymentMethod paymentMethod;
}
