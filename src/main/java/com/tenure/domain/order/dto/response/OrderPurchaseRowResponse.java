package com.tenure.domain.order.dto.response;

import com.tenure.domain.offer.enums.OfferStatus;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.order.enums.OrderFeedRowType;
import com.tenure.domain.order.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 구매 내역 목록 한 행. 판매 내역과 대칭이다 — 아이템 단위 집계가 없고(구매자는
 * 아이템당 1건), offerStatus 에 PENDING 외 값(거절·만료)도 실린다
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "구매 내역 목록 행")
public class OrderPurchaseRowResponse {

	@Schema(description = "행 종류", example = "OFFER")
	private final OrderFeedRowType rowType;

	@Schema(description = "OFFER 행 — 제안서 상세 화면 이동 키", example = "1")
	private final Long offerId;

	@Schema(description = "OFFER 행", example = "PURCHASE_OFFER")
	private final OfferType offerType;

	@Schema(description = "OFFER 행 — PENDING 외 값도 실린다", example = "PENDING")
	private final OfferStatus offerStatus;

	@Schema(description = "OFFER 행", example = "360000")
	private final String offerPrice;

	@Schema(description = "OFFER 행 — 제안 만료까지", example = "2026-06-22T18:30:00")
	private final LocalDateTime expiresAt;

	@Schema(description = "ORDER 행 — 거래 상세 화면 이동 키", example = "1")
	private final Long orderId;

	@Schema(description = "ORDER 행", example = "2026042919502395")
	private final String orderNo;

	@Schema(description = "ORDER 행", example = "SHIPPED")
	private final OrderStatus orderStatus;

	@Schema(description = "ORDER 행 — 총 결제 금액", example = "386600")
	private final String totalPaymentAmount;

	@Schema(description = "판매자")
	private final OrderPartySummaryResponse counterpart;

	@Schema(description = "상품 정보")
	private final OrderItemSummaryResponse item;

	@Schema(description = "정렬 키", example = "2026-06-21T18:30:00")
	private final LocalDateTime createdAt;
}
