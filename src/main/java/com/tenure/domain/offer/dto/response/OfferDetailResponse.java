package com.tenure.domain.offer.dto.response;

import com.tenure.domain.offer.enums.OfferStatus;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.payment.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 제안서 상세. 구매자·판매자 시점에 따라 보여줄 금액 블록이 갈림.
 *
 * 구매자 시점이면 totalPaymentAmount·paymentMethod 가 채워지고 expectedPayoutAmount·
 * payoutAccount 는 null, 판매자 시점이면 반대임
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "제안서 상세")
public class OfferDetailResponse {

	@Schema(description = "제안 id", example = "1")
	private final Long id;

	@Schema(description = "구매 제안 / 거래 의사", example = "PURCHASE_OFFER")
	private final OfferType offerType;

	@Schema(description = "제안 상태", example = "PENDING")
	private final OfferStatus status;

	@Schema(description = "제안 만료 시각", example = "2026-06-21T18:30:00")
	private final LocalDateTime expiresAt;

	@Schema(description = "상품 정보")
	private final OfferItemSummaryResponse item;

	@Schema(description = "제안 금액", example = "360000")
	private final String offerPrice;

	@Schema(description = "배송비", example = "5000")
	private final String shippingFee;

	@Schema(description = "수수료", example = "21600")
	private final String feeAmount;

	@Schema(description = "구매자 시점 — 총 결제 금액. 판매자 시점이면 null", example = "386600")
	private final String totalPaymentAmount;

	@Schema(description = "판매자 시점 — 총 정산 예정 금액. 구매자 시점이면 null", example = "365000")
	private final String expectedPayoutAmount;

	@Schema(description = "배송지")
	private final OfferShippingResponse shipping;

	@Schema(description = "구매자 요청사항", example = "직거래도 가능할까요?")
	private final String buyerRequest;

	@Schema(description = "판매자 시점 — 정산 계좌. 구매자 시점이면 null", example = "토스뱅크 1234-1234-1234")
	private final String payoutAccount;

	@Schema(description = "상대방")
	private final OfferCounterpartResponse counterpart;

	@Schema(description = "구매자 시점 — 결제 수단. 판매자 시점이면 null", example = "TOSS_PAY")
	private final PaymentMethod paymentMethod;
}
