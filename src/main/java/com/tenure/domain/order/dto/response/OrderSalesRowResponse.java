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
 * 판매 내역 목록 한 행. rowType=OFFER 면 offer 쪽 필드가, ORDER 면 order 쪽 필드가 채워지고
 * 반대쪽은 null 이다.
 *
 * PENDING 탭의 OFFER 행은 아이템 단위로 묶인다 — offerId 는 묶음 중 가장 최근 제안의 id 다
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "판매 내역 목록 행")
public class OrderSalesRowResponse {

	@Schema(description = "행 종류", example = "OFFER")
	private final OrderFeedRowType rowType;

	@Schema(description = "OFFER 행 — 묶음 중 가장 최근 제안의 id. 제안서 상세 화면 이동 키", example = "1")
	private final Long offerId;

	@Schema(description = "OFFER 행 — 아이템 단위 집계의 키. 받은 제안 목록 화면 이동 키", example = "1")
	private final Long itemId;

	@Schema(description = "OFFER 행 — 이 아이템에 걸린 PENDING 제안 건수", example = "4")
	private final Integer pendingOfferCount;

	@Schema(description = "OFFER 행", example = "PURCHASE_OFFER")
	private final OfferType offerType;

	@Schema(description = "OFFER 행 — 목록은 PENDING 만 싣는다", example = "PENDING")
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

	@Schema(description = "ORDER 행 — 총 정산 금액", example = "365000")
	private final String payoutAmount;

	@Schema(description = "구매자")
	private final OrderPartySummaryResponse counterpart;

	@Schema(description = "상품 정보")
	private final OrderItemSummaryResponse item;

	@Schema(description = "정렬 키", example = "2026-06-21T18:30:00")
	private final LocalDateTime createdAt;
}
