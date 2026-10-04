package com.tenure.domain.offer.mapper;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.offer.dto.response.OfferAcceptResponse;
import com.tenure.domain.offer.dto.response.OfferBuyerSummaryResponse;
import com.tenure.domain.offer.dto.response.OfferCounterpartResponse;
import com.tenure.domain.offer.dto.response.OfferCreateResponse;
import com.tenure.domain.offer.dto.response.OfferDetailResponse;
import com.tenure.domain.offer.dto.response.OfferItemSummaryResponse;
import com.tenure.domain.offer.dto.response.OfferPreviewResponse;
import com.tenure.domain.offer.dto.response.OfferReceivedHeaderResponse;
import com.tenure.domain.offer.dto.response.OfferReceivedListResponse;
import com.tenure.domain.offer.dto.response.OfferReceivedRowResponse;
import com.tenure.domain.offer.dto.response.OfferShippingResponse;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.payment.enums.PaymentMethod;
import com.tenure.domain.user.entity.User;
import java.math.BigDecimal;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 엔티티 → 응답 단방향 변환.
 *
 * 저장소 키를 URL 로 바꾸는 일과 금액을 문자열로 바꾸는 일은 Service 가 끝내고 넘김 — 여기는
 * 이름이 맞는 값을 그대로 옮기기만 함
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OfferMapper {

	@Mapping(target = "itemPrice", source = "itemPrice")
	@Mapping(target = "shippingFee", source = "shippingFee")
	@Mapping(target = "feeRate", source = "feeRate")
	@Mapping(target = "feeAmount", source = "feeAmount")
	@Mapping(target = "buyerFeeAmount", source = "buyerFeeAmount")
	@Mapping(target = "sellerFeeAmount", source = "sellerFeeAmount")
	@Mapping(target = "totalPaymentAmount", source = "totalPaymentAmount")
	@Mapping(target = "expectedPayoutAmount", source = "expectedPayoutAmount")
	OfferPreviewResponse toPreviewResponse(
			String itemPrice, String shippingFee, BigDecimal feeRate, String feeAmount,
			String buyerFeeAmount, String sellerFeeAmount, String totalPaymentAmount, String expectedPayoutAmount
	);

	@Mapping(target = "offerId", source = "offer.id")
	@Mapping(target = "expiresAt", source = "offer.expiresAt")
	@Mapping(target = "totalPaymentAmount", source = "totalPaymentAmount")
	OfferCreateResponse toCreateResponse(PurchaseOffer offer, String totalPaymentAmount);

	@Mapping(target = "brandName", source = "item.brandName")
	@Mapping(target = "name", source = "item.name")
	@Mapping(target = "coverImageUrl", source = "coverImageUrl")
	OfferItemSummaryResponse toItemSummaryResponse(Item item, String coverImageUrl);

	@Mapping(target = "recipientName", source = "offer.shippingRecipientName")
	@Mapping(target = "phone", source = "offer.shippingPhone")
	@Mapping(target = "address", source = "offer.shippingAddress")
	@Mapping(target = "addressDetail", source = "offer.shippingAddressDetail")
	OfferShippingResponse toShippingResponse(PurchaseOffer offer);

	@Mapping(target = "nickname", source = "counterpart.nickname")
	OfferCounterpartResponse toCounterpartResponse(User counterpart);

	@Mapping(target = "id", source = "offer.id")
	@Mapping(target = "offerType", source = "offer.offerType")
	@Mapping(target = "status", source = "offer.status")
	@Mapping(target = "expiresAt", source = "offer.expiresAt")
	@Mapping(target = "item", source = "item")
	@Mapping(target = "offerPrice", source = "offerPrice")
	@Mapping(target = "shippingFee", source = "shippingFee")
	@Mapping(target = "feeAmount", source = "feeAmount")
	@Mapping(target = "totalPaymentAmount", source = "totalPaymentAmount")
	@Mapping(target = "expectedPayoutAmount", source = "expectedPayoutAmount")
	@Mapping(target = "shipping", source = "shipping")
	@Mapping(target = "buyerRequest", source = "offer.buyerRequest")
	@Mapping(target = "payoutAccount", source = "payoutAccount")
	@Mapping(target = "counterpart", source = "counterpart")
	@Mapping(target = "paymentMethod", source = "paymentMethod")
	OfferDetailResponse toDetailResponse(
			PurchaseOffer offer, OfferItemSummaryResponse item, String offerPrice, String shippingFee,
			String feeAmount, String totalPaymentAmount, String expectedPayoutAmount,
			OfferShippingResponse shipping, String payoutAccount, OfferCounterpartResponse counterpart,
			PaymentMethod paymentMethod
	);

	@Mapping(target = "orderId", source = "order.id")
	@Mapping(target = "orderNo", source = "order.orderNo")
	@Mapping(target = "autoCanceledCount", source = "autoCanceledCount")
	OfferAcceptResponse toAcceptResponse(TradeOrder order, Integer autoCanceledCount);

	@Mapping(target = "nickname", source = "buyer.nickname")
	@Mapping(target = "profileImageUrl", source = "profileImageUrl")
	OfferBuyerSummaryResponse toBuyerSummaryResponse(User buyer, String profileImageUrl);

	@Mapping(target = "offerId", source = "offer.id")
	@Mapping(target = "buyer", source = "buyer")
	@Mapping(target = "offerPrice", source = "offerPrice")
	@Mapping(target = "expiresAt", source = "offer.expiresAt")
	OfferReceivedRowResponse toReceivedRowResponse(
			PurchaseOffer offer, OfferBuyerSummaryResponse buyer, String offerPrice);

	@Mapping(target = "brandName", source = "item.brandName")
	@Mapping(target = "name", source = "item.name")
	@Mapping(target = "coverImageUrl", source = "coverImageUrl")
	@Mapping(target = "saleStatus", source = "item.saleStatus")
	@Mapping(target = "price", source = "price")
	OfferReceivedHeaderResponse toReceivedHeaderResponse(Item item, String coverImageUrl, String price);

	@Mapping(target = "item", source = "item")
	@Mapping(target = "offerType", source = "offerType")
	@Mapping(target = "totalCount", source = "totalCount")
	@Mapping(target = "offers", source = "offers")
	OfferReceivedListResponse toReceivedListResponse(
			OfferReceivedHeaderResponse item, OfferType offerType, Integer totalCount,
			List<OfferReceivedRowResponse> offers);
}
