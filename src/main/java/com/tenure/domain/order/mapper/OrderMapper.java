package com.tenure.domain.order.mapper;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.order.dto.response.CancelResponse;
import com.tenure.domain.order.dto.response.CourierResponse;
import com.tenure.domain.order.dto.response.DeliverResponse;
import com.tenure.domain.order.dto.response.ConfirmResponse;
import com.tenure.domain.order.dto.response.OrderDetailResponse;
import com.tenure.domain.order.dto.response.OrderItemSummaryResponse;
import com.tenure.domain.order.dto.response.OrderPartySummaryResponse;
import com.tenure.domain.order.dto.response.OrderPaymentResponse;
import com.tenure.domain.order.dto.response.OrderPurchaseRowResponse;
import com.tenure.domain.order.dto.response.OrderSalesRowResponse;
import com.tenure.domain.order.dto.response.OrderSettlementResponse;
import com.tenure.domain.order.dto.response.OrderShipmentResponse;
import com.tenure.domain.order.dto.response.OrderStatusHistoryResponse;
import com.tenure.domain.order.dto.response.ShipmentUpdateResponse;
import com.tenure.domain.order.entity.OrderShipment;
import com.tenure.domain.order.entity.OrderStatusHistory;
import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.order.enums.CourierCode;
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.order.enums.OrderViewerRole;
import com.tenure.domain.payment.entity.Payment;
import com.tenure.domain.payout.enums.BankCode;
import com.tenure.domain.settlement.entity.Settlement;
import com.tenure.domain.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 엔티티 → 응답 단방향 변환.
 *
 * 저장소 키를 URL 로 바꾸는 일, 금액을 문자열로 바꾸는 일, 배송 조회 URL 을 조합하는 일은
 * Service 가 끝내고 넘김 — 여기는 이름이 맞는 값을 그대로 옮기기만 함
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderMapper {

	@Mapping(target = "brandName", source = "item.brandName")
	@Mapping(target = "name", source = "item.name")
	@Mapping(target = "coverImageUrl", source = "coverImageUrl")
	OrderItemSummaryResponse toItemSummaryResponse(Item item, String coverImageUrl);

	@Mapping(target = "nickname", source = "user.nickname")
	@Mapping(target = "profileImageUrl", source = "profileImageUrl")
	OrderPartySummaryResponse toPartySummaryResponse(User user, String profileImageUrl);

	@Mapping(target = "courierCode", source = "shipment.courierCode")
	@Mapping(target = "trackingNo", source = "shipment.trackingNo")
	@Mapping(target = "trackingUrl", source = "trackingUrl")
	@Mapping(target = "recipientName", source = "shipment.recipientName")
	@Mapping(target = "phone", source = "shipment.phone")
	@Mapping(target = "address", source = "shipment.address")
	@Mapping(target = "addressDetail", source = "shipment.addressDetail")
	@Mapping(target = "deliveryRequestType", source = "shipment.deliveryRequestType")
	@Mapping(target = "deliveryRequest", source = "shipment.deliveryRequest")
	OrderShipmentResponse toShipmentResponse(OrderShipment shipment, String trackingUrl);

	@Mapping(target = "method", source = "method")
	@Mapping(target = "status", source = "status")
	OrderPaymentResponse toPaymentResponse(Payment payment);

	@Mapping(target = "bankCode", source = "bankCode")
	@Mapping(target = "accountNumber", source = "settlement.accountNumber")
	@Mapping(target = "settledAt", source = "settlement.settledAt")
	OrderSettlementResponse toSettlementResponse(Settlement settlement, BankCode bankCode);

	@Mapping(target = "status", source = "status")
	@Mapping(target = "changedAt", source = "changedAt")
	OrderStatusHistoryResponse toStatusHistoryResponse(OrderStatusHistory history);

	@Mapping(target = "id", source = "order.id")
	@Mapping(target = "orderNo", source = "order.orderNo")
	@Mapping(target = "status", source = "order.status")
	@Mapping(target = "viewerRole", source = "viewerRole")
	@Mapping(target = "item", source = "item")
	@Mapping(target = "buyer", source = "buyer")
	@Mapping(target = "seller", source = "seller")
	@Mapping(target = "statusHistory", source = "statusHistory")
	@Mapping(target = "buyerRequest", source = "buyerRequest")
	@Mapping(target = "shipment", source = "shipment")
	@Mapping(target = "itemPrice", source = "itemPrice")
	@Mapping(target = "shippingFee", source = "shippingFee")
	@Mapping(target = "feeAmount", source = "feeAmount")
	@Mapping(target = "totalPaymentAmount", source = "totalPaymentAmount")
	@Mapping(target = "payoutAmount", source = "payoutAmount")
	@Mapping(target = "payment", source = "payment")
	@Mapping(target = "settlement", source = "settlement")
	@Mapping(target = "autoConfirmAt", source = "order.autoConfirmAt")
	OrderDetailResponse toDetailResponse(
			TradeOrder order, OrderViewerRole viewerRole, OrderItemSummaryResponse item,
			OrderPartySummaryResponse buyer, OrderPartySummaryResponse seller,
			List<OrderStatusHistoryResponse> statusHistory, String buyerRequest, OrderShipmentResponse shipment,
			String itemPrice, String shippingFee, String feeAmount, String totalPaymentAmount, String payoutAmount,
			OrderPaymentResponse payment, OrderSettlementResponse settlement
	);

	@Mapping(target = "code", source = "code")
	@Mapping(target = "name", source = "name")
	CourierResponse toCourierResponse(CourierCode code, String name);

	@Mapping(target = "status", source = "status")
	ShipmentUpdateResponse toShipmentUpdateResponse(OrderStatus status);

	@Mapping(target = "status", source = "status")
	@Mapping(target = "autoConfirmAt", source = "autoConfirmAt")
	DeliverResponse toDeliverResponse(OrderStatus status, LocalDateTime autoConfirmAt);

	@Mapping(target = "status", source = "status")
	@Mapping(target = "settlementScheduledAt", source = "settlementScheduledAt")
	ConfirmResponse toConfirmResponse(OrderStatus status, LocalDate settlementScheduledAt);

	@Mapping(target = "status", source = "status")
	@Mapping(target = "refundedAt", source = "refundedAt")
	CancelResponse toCancelResponse(OrderStatus status, LocalDateTime refundedAt);

	/** 판매 내역 — PENDING 제안 묶음 1건 (아이템 단위) */
	@Mapping(target = "rowType", constant = "OFFER")
	@Mapping(target = "offerId", source = "latestOffer.id")
	@Mapping(target = "itemId", source = "itemId")
	@Mapping(target = "pendingOfferCount", source = "pendingOfferCount")
	@Mapping(target = "offerType", source = "latestOffer.offerType")
	@Mapping(target = "offerStatus", source = "latestOffer.status")
	@Mapping(target = "offerPrice", source = "offerPrice")
	@Mapping(target = "expiresAt", source = "latestOffer.expiresAt")
	@Mapping(target = "orderId", ignore = true)
	@Mapping(target = "orderNo", ignore = true)
	@Mapping(target = "orderStatus", ignore = true)
	@Mapping(target = "payoutAmount", ignore = true)
	@Mapping(target = "counterpart", source = "counterpart")
	@Mapping(target = "item", source = "item")
	@Mapping(target = "createdAt", source = "latestOffer.createdAt")
	OrderSalesRowResponse toSalesRowFromOffer(
			PurchaseOffer latestOffer, Long itemId, Integer pendingOfferCount, String offerPrice,
			OrderPartySummaryResponse counterpart, OrderItemSummaryResponse item
	);

	/** 판매 내역 — 거래 1건 */
	@Mapping(target = "rowType", constant = "ORDER")
	@Mapping(target = "offerId", ignore = true)
	@Mapping(target = "itemId", ignore = true)
	@Mapping(target = "pendingOfferCount", ignore = true)
	@Mapping(target = "offerType", ignore = true)
	@Mapping(target = "offerStatus", ignore = true)
	@Mapping(target = "offerPrice", ignore = true)
	@Mapping(target = "expiresAt", ignore = true)
	@Mapping(target = "orderId", source = "order.id")
	@Mapping(target = "orderNo", source = "order.orderNo")
	@Mapping(target = "orderStatus", source = "order.status")
	@Mapping(target = "payoutAmount", source = "payoutAmount")
	@Mapping(target = "counterpart", source = "counterpart")
	@Mapping(target = "item", source = "item")
	@Mapping(target = "createdAt", source = "order.createdAt")
	OrderSalesRowResponse toSalesRowFromOrder(
			TradeOrder order, String payoutAmount, OrderPartySummaryResponse counterpart, OrderItemSummaryResponse item
	);

	/** 구매 내역 — 보낸 제안 1건 (아이템 단위 집계 없음) */
	@Mapping(target = "rowType", constant = "OFFER")
	@Mapping(target = "offerId", source = "offer.id")
	@Mapping(target = "offerType", source = "offer.offerType")
	@Mapping(target = "offerStatus", source = "offer.status")
	@Mapping(target = "offerPrice", source = "offerPrice")
	@Mapping(target = "expiresAt", source = "offer.expiresAt")
	@Mapping(target = "orderId", ignore = true)
	@Mapping(target = "orderNo", ignore = true)
	@Mapping(target = "orderStatus", ignore = true)
	@Mapping(target = "totalPaymentAmount", ignore = true)
	@Mapping(target = "counterpart", source = "counterpart")
	@Mapping(target = "item", source = "item")
	@Mapping(target = "createdAt", source = "offer.createdAt")
	OrderPurchaseRowResponse toPurchaseRowFromOffer(
			PurchaseOffer offer, String offerPrice, OrderPartySummaryResponse counterpart, OrderItemSummaryResponse item
	);

	/** 구매 내역 — 거래 1건 */
	@Mapping(target = "rowType", constant = "ORDER")
	@Mapping(target = "offerId", ignore = true)
	@Mapping(target = "offerType", ignore = true)
	@Mapping(target = "offerStatus", ignore = true)
	@Mapping(target = "offerPrice", ignore = true)
	@Mapping(target = "expiresAt", ignore = true)
	@Mapping(target = "orderId", source = "order.id")
	@Mapping(target = "orderNo", source = "order.orderNo")
	@Mapping(target = "orderStatus", source = "order.status")
	@Mapping(target = "totalPaymentAmount", source = "totalPaymentAmount")
	@Mapping(target = "counterpart", source = "counterpart")
	@Mapping(target = "item", source = "item")
	@Mapping(target = "createdAt", source = "order.createdAt")
	OrderPurchaseRowResponse toPurchaseRowFromOrder(
			TradeOrder order, String totalPaymentAmount, OrderPartySummaryResponse counterpart, OrderItemSummaryResponse item
	);
}
