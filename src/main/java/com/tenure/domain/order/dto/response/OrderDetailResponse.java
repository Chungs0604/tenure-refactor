package com.tenure.domain.order.dto.response;

import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.order.enums.OrderViewerRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 거래 상세. 구매자·판매자 정보는 뷰어 역할과 무관하게 둘 다 내려가고, settlement 만 SELLER 전용이다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "거래 상세")
public class OrderDetailResponse {

	@Schema(description = "거래 id", example = "1")
	private final Long id;

	@Schema(description = "주문 번호", example = "2026042919502395")
	private final String orderNo;

	@Schema(description = "거래 단계", example = "SHIPPED")
	private final OrderStatus status;

	@Schema(description = "조회자 역할", example = "BUYER")
	private final OrderViewerRole viewerRole;

	@Schema(description = "상품 정보")
	private final OrderItemSummaryResponse item;

	@Schema(description = "구매자 정보")
	private final OrderPartySummaryResponse buyer;

	@Schema(description = "판매자 정보")
	private final OrderPartySummaryResponse seller;

	@Schema(description = "타임라인")
	private final List<OrderStatusHistoryResponse> statusHistory;

	@Schema(description = "구매자 요청사항. purchase_offer.buyer_request 스냅샷", example = "직거래도 가능할까요?")
	private final String buyerRequest;

	@Schema(description = "배송 정보")
	private final OrderShipmentResponse shipment;

	@Schema(description = "상품 금액", example = "360000")
	private final String itemPrice;

	@Schema(description = "배송비", example = "5000")
	private final String shippingFee;

	@Schema(description = "수수료", example = "21600")
	private final String feeAmount;

	@Schema(description = "총 결제 금액", example = "386600")
	private final String totalPaymentAmount;

	@Schema(description = "총 정산 금액", example = "365000")
	private final String payoutAmount;

	@Schema(description = "결제 정보")
	private final OrderPaymentResponse payment;

	@Schema(description = "정산 정보. SELLER 시점에만 채워짐")
	private final OrderSettlementResponse settlement;

	@Schema(description = "자동 구매 확정 시각. 배송 완료 전엔 null", example = "2026-06-04T10:00:00")
	private final LocalDateTime autoConfirmAt;
}
