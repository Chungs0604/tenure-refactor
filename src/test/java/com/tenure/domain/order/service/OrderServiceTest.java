package com.tenure.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.offer.enums.OfferStatus;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.offer.repository.PurchaseOfferRepository;
import com.tenure.domain.order.dto.response.CancelResponse;
import com.tenure.domain.order.dto.response.ConfirmResponse;
import com.tenure.domain.order.dto.response.CourierResponse;
import com.tenure.domain.order.dto.response.DeliverResponse;
import com.tenure.domain.order.dto.response.OrderDetailResponse;
import com.tenure.domain.order.dto.response.OrderPurchaseRowResponse;
import com.tenure.domain.order.dto.response.OrderSalesRowResponse;
import com.tenure.domain.order.dto.response.ShipmentUpdateResponse;
import com.tenure.domain.order.entity.OrderShipment;
import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.order.enums.CourierCode;
import com.tenure.domain.order.enums.OrderFeedRowType;
import com.tenure.domain.order.enums.OrderFeedTab;
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.order.enums.OrderViewerRole;
import com.tenure.domain.order.exception.OrderErrorCode;
import com.tenure.domain.order.mapper.OrderMapper;
import com.tenure.domain.order.mapper.OrderMapperImpl;
import com.tenure.domain.order.repository.OrderShipmentRepository;
import com.tenure.domain.order.repository.OrderStatusHistoryRepository;
import com.tenure.domain.order.repository.TradeOrderRepository;
import com.tenure.domain.payment.entity.Payment;
import com.tenure.domain.payment.enums.PaymentMethod;
import com.tenure.domain.payment.repository.PaymentRepository;
import com.tenure.domain.payment.service.PaymentService;
import com.tenure.domain.payout.entity.PayoutAccount;
import com.tenure.domain.payout.enums.BankCode;
import com.tenure.domain.payout.exception.PayoutErrorCode;
import com.tenure.domain.payout.repository.PayoutAccountRepository;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.settlement.entity.Settlement;
import com.tenure.domain.settlement.repository.SettlementRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import com.tenure.global.holiday.BusinessDayCalculator;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.storage.FileStorage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderService")
class OrderServiceTest {

	private static final Long BUYER_ID = 1L;
	private static final Long SELLER_ID = 2L;
	private static final Long OTHER_ID = 3L;
	private static final Long ORDER_ID = 100L;
	private static final Long ITEM_ID = 200L;
	private static final Long OFFER_ID = 300L;

	@Mock private TradeOrderRepository tradeOrderRepository;
	@Mock private OrderShipmentRepository orderShipmentRepository;
	@Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
	@Mock private SettlementRepository settlementRepository;
	@Mock private PurchaseOfferRepository purchaseOfferRepository;
	@Mock private PaymentRepository paymentRepository;
	@Mock private PaymentService paymentService;
	@Mock private PayoutAccountRepository payoutAccountRepository;
	@Mock private ItemOwnershipRepository itemOwnershipRepository;
	@Mock private NotificationService notificationService;
	@Mock private FileStorage fileStorage;
	@Mock private BusinessDayCalculator businessDayCalculator;

	// 매핑은 검증 대상이라 mock 이 아니라 실제 생성 구현을 넣는다
	@Spy private OrderMapper orderMapper = new OrderMapperImpl();
	// 순수 계산(URL 조합)이라 mock 하지 않는다
	@Spy private TrackingUrlResolver trackingUrlResolver = new TrackingUrlResolver();

	@InjectMocks private OrderService orderService;

	@Nested
	@DisplayName("거래 상세")
	class GetDetail {

		@Test
		@DisplayName("구매자가 조회하면 settlement 은 비어 있다")
		void getDetail_buyer_settlementIsNull() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			given(tradeOrderRepository.findDetailById(ORDER_ID)).willReturn(Optional.of(order));
			given(orderShipmentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
			given(orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(ORDER_ID)).willReturn(List.of());
			given(paymentRepository.findByOfferId(order.getOffer().getId())).willReturn(Optional.empty());

			OrderDetailResponse response = orderService.getDetail(BUYER_ID, ORDER_ID);

			assertThat(response.getViewerRole()).isEqualTo(OrderViewerRole.BUYER);
			assertThat(response.getSettlement()).isNull();
			assertThat(response.getBuyer()).isNotNull();
			assertThat(response.getSeller()).isNotNull();
			assertThat(response.getBuyerRequest()).isEqualTo(order.getOffer().getBuyerRequest());
			verify(settlementRepository, never()).findByOrderId(any());
		}

		@Test
		@DisplayName("판매자가 조회하면 settlement 이 있으면 채운다")
		void getDetail_seller_settlementFilled() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.SETTLED);
			Settlement settlement = Settlement.of(order, seller, BankCode.TOSS_BANK.name(), "1111-1111", "홍길동", 365000);
			given(tradeOrderRepository.findDetailById(ORDER_ID)).willReturn(Optional.of(order));
			given(orderShipmentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
			given(orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(ORDER_ID)).willReturn(List.of());
			given(paymentRepository.findByOfferId(order.getOffer().getId())).willReturn(Optional.empty());
			given(settlementRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(settlement));

			OrderDetailResponse response = orderService.getDetail(SELLER_ID, ORDER_ID);

			assertThat(response.getViewerRole()).isEqualTo(OrderViewerRole.SELLER);
			assertThat(response.getSettlement()).isNotNull();
			assertThat(response.getSettlement().getAccountNumber()).isEqualTo("1111-1111");
		}

		@Test
		@DisplayName("없는 거래면 ORDER_404 다")
		void getDetail_unknownOrder_notFound() {
			given(tradeOrderRepository.findDetailById(ORDER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> orderService.getDetail(BUYER_ID, ORDER_ID))
					.isInstanceOf(CustomException.class)
					.extracting(OrderServiceTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_404);
		}

		@Test
		@DisplayName("구매자도 판매자도 아니면 ORDER_403 이다")
		void getDetail_unrelatedUser_forbidden() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			given(tradeOrderRepository.findDetailById(ORDER_ID)).willReturn(Optional.of(order));

			assertThatThrownBy(() -> orderService.getDetail(OTHER_ID, ORDER_ID))
					.isInstanceOf(CustomException.class)
					.extracting(OrderServiceTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_403);
		}
	}

	@Nested
	@DisplayName("택배사 목록")
	class GetCouriers {

		@Test
		@DisplayName("4종을 한글 라벨과 함께 돌려준다")
		void getCouriers_returnsFourWithLabels() {
			List<CourierResponse> couriers = orderService.getCouriers();

			assertThat(couriers).hasSize(4);
			assertThat(couriers).extracting(CourierResponse::getCode)
					.containsExactlyInAnyOrder(CourierCode.values());
			assertThat(couriers).filteredOn(c -> c.getCode() == CourierCode.CJ_LOGISTICS)
					.extracting(CourierResponse::getName)
					.containsExactly("CJ대한통운");
		}
	}

	@Nested
	@DisplayName("운송장 번호 입력")
	class UpdateShipment {

		@Test
		@DisplayName("최초 입력이면 발송 처리하고 이력·알림을 남긴다")
		void updateShipment_firstTime_shipsAndNotifies() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			OrderShipment shipment = shipmentOf(order);
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderShipmentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(shipment));

			ShipmentUpdateResponse response =
					orderService.updateShipment(SELLER_ID, ORDER_ID, CourierCode.CJ_LOGISTICS.name(), "1234123412349");

			assertThat(response.getStatus()).isEqualTo(OrderStatus.SHIPPED);
			assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
			assertThat(shipment.getShippedAt()).isNotNull();
			verify(orderStatusHistoryRepository, times(1)).save(any());
			verify(notificationService, times(1)).notify(
					eq(buyer), any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("이미 발송된 거래에 재입력하면 이력을 다시 쌓지 않는다")
		void updateShipment_alreadyShipped_doesNotDuplicateHistory() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			OrderShipment shipment = shipmentOf(order);
			shipment.updateTracking(CourierCode.CJ_LOGISTICS, "1111111111111", LocalDateTime.now());
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderShipmentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(shipment));

			orderService.updateShipment(SELLER_ID, ORDER_ID, CourierCode.KOREA_POST.name(), "2222222222222");

			assertThat(shipment.getTrackingNo()).isEqualTo("2222222222222");
			verify(orderStatusHistoryRepository, never()).save(any());
			verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("판매자가 아니면 ORDER_403 이다")
		void updateShipment_notSeller_forbidden() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			assertThatThrownBy(() -> orderService.updateShipment(
					OTHER_ID, ORDER_ID, CourierCode.CJ_LOGISTICS.name(), "1234123412349"))
					.isInstanceOf(CustomException.class)
					.extracting(OrderServiceTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_403);
		}

		@Test
		@DisplayName("발송할 수 없는 단계면 ORDER_409_STATUS 다")
		void updateShipment_wrongStatus_conflict() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			order.deliver(LocalDateTime.now().plusHours(72));
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			assertThatThrownBy(() -> orderService.updateShipment(
					SELLER_ID, ORDER_ID, CourierCode.CJ_LOGISTICS.name(), "1234123412349"))
					.isInstanceOf(CustomException.class)
					.extracting(OrderServiceTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_409_STATUS);
		}

		@Test
		@DisplayName("운송장 번호가 숫자가 아니면 ORDER_400_TRACKING 이다")
		void updateShipment_nonDigitTrackingNo_badRequest() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			assertThatThrownBy(() -> orderService.updateShipment(
					SELLER_ID, ORDER_ID, CourierCode.CJ_LOGISTICS.name(), "abc123"))
					.isInstanceOf(CustomException.class)
					.extracting(OrderServiceTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_400_TRACKING);
		}

		@Test
		@DisplayName("택배사를 고르지 않으면 ORDER_400_TRACKING 이다")
		void updateShipment_nullCourier_badRequest() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			assertThatThrownBy(() -> orderService.updateShipment(
					SELLER_ID, ORDER_ID, null, "1234123412349"))
					.isInstanceOf(CustomException.class)
					.extracting(OrderServiceTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_400_TRACKING);
		}

		@Test
		@DisplayName("없는 거래면 ORDER_404_TRACKING 이다 — 다른 엔드포인트와 코드가 다르다")
		void updateShipment_unknownOrder_notFoundWithTrackingCode() {
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> orderService.updateShipment(
					SELLER_ID, ORDER_ID, CourierCode.CJ_LOGISTICS.name(), "1234123412349"))
					.isInstanceOf(CustomException.class)
					.extracting(OrderServiceTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_404_TRACKING);
		}

		@Test
		@DisplayName("거래는 있는데 배송 행이 없으면 ORDER_404_TRACKING 이다")
		void updateShipment_missingShipmentRow_notFoundWithTrackingCode() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderShipmentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> orderService.updateShipment(
					SELLER_ID, ORDER_ID, CourierCode.CJ_LOGISTICS.name(), "1234123412349"))
					.isInstanceOf(CustomException.class)
					.extracting(OrderServiceTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_404_TRACKING);
		}
	}

	@Nested
	@DisplayName("배송 완료 처리")
	class Deliver {

		@Test
		@DisplayName("발송된 거래를 배송 완료로 바꾸고 72시간 뒤 자동 확정을 건다")
		void deliver_shipped_movesToDeliveredWithAutoConfirm() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			OrderShipment shipment = shipmentOf(order);
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderShipmentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(shipment));

			DeliverResponse response = orderService.deliver(SELLER_ID, ORDER_ID);

			assertThat(response.getStatus()).isEqualTo(OrderStatus.DELIVERED);
			assertThat(order.getAutoConfirmAt()).isEqualTo(response.getAutoConfirmAt());
			assertThat(shipment.getDeliveredAt()).isNotNull();
			verify(orderStatusHistoryRepository).save(any());
			verify(notificationService).notify(eq(buyer), any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("판매자가 아니면 ORDER_403 이다")
		void deliver_notSeller_forbidden() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			assertThatThrownBy(() -> orderService.deliver(OTHER_ID, ORDER_ID))
					.isInstanceOf(CustomException.class)
					.extracting(OrderServiceTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_403);
		}

		@Test
		@DisplayName("발송 전이면 ORDER_409_STATUS 다")
		void deliver_notShipped_conflict() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			assertThatThrownBy(() -> orderService.deliver(SELLER_ID, ORDER_ID))
					.isInstanceOf(CustomException.class)
					.extracting(OrderServiceTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_409_STATUS);
		}
	}

	@Nested
	@DisplayName("구매 확정")
	class Confirm {

		@Test
		@DisplayName("배송완료 거래를 확정하면 소유권을 승계하고 정산을 생성한다")
		void confirm_delivered_transfersOwnershipAndCreatesSettlement() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(seller);
			TradeOrder order = order(buyer, seller, item, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			order.deliver(LocalDateTime.now().plusHours(72));
			ItemOwnership current = ItemOwnership.openInitial(item, seller, LocalDate.of(2025, 2, 8));
			PayoutAccount account = PayoutAccount.register(seller, BankCode.TOSS_BANK, "홍길동", "1111-1111", true);
			LocalDate scheduledAt = LocalDate.of(2026, 6, 10);

			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(itemOwnershipRepository.findCurrent(ITEM_ID)).willReturn(Optional.of(current));
			given(payoutAccountRepository.findAllOf(SELLER_ID)).willReturn(List.of(account));
			given(businessDayCalculator.plusBusinessDays(any(), eq(2))).willReturn(scheduledAt);

			ConfirmResponse response = orderService.confirm(BUYER_ID, ORDER_ID);

			assertThat(response.getStatus()).isEqualTo(OrderStatus.PURCHASE_CONFIRMED);
			assertThat(response.getSettlementScheduledAt()).isEqualTo(scheduledAt);
			assertThat(current.isClosed()).isTrue();
			assertThat(item.getSaleStatus()).isEqualTo(SaleStatus.SOLD);
			verify(itemOwnershipRepository).save(any(ItemOwnership.class));
			verify(settlementRepository).save(any(Settlement.class));
			verify(notificationService).notify(eq(seller), any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("구매자가 아니면 ORDER_403 이다")
		void confirm_notBuyer_forbidden() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			order.deliver(LocalDateTime.now().plusHours(72));
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			assertThatThrownBy(() -> orderService.confirm(OTHER_ID, ORDER_ID))
					.isInstanceOf(CustomException.class)
					.extracting(OrderServiceTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_403);
		}

		@Test
		@DisplayName("배송완료 전이면 ORDER_409_STATUS 다")
		void confirm_notDelivered_conflict() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			assertThatThrownBy(() -> orderService.confirm(BUYER_ID, ORDER_ID))
					.isInstanceOf(CustomException.class)
					.extracting(OrderServiceTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_409_STATUS);
		}

		@Test
		@DisplayName("정산 계좌가 없으면 PAYOUT_400_REQUIRED 다")
		void confirm_noPayoutAccount_rejected() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(seller);
			TradeOrder order = order(buyer, seller, item, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			order.deliver(LocalDateTime.now().plusHours(72));
			ItemOwnership current = ItemOwnership.openInitial(item, seller, LocalDate.of(2025, 2, 8));

			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(itemOwnershipRepository.findCurrent(ITEM_ID)).willReturn(Optional.of(current));
			given(payoutAccountRepository.findAllOf(SELLER_ID)).willReturn(List.of());

			assertThatThrownBy(() -> orderService.confirm(BUYER_ID, ORDER_ID))
					.isInstanceOf(CustomException.class)
					.extracting(OrderServiceTest::errorCodeOf)
					.isEqualTo(PayoutErrorCode.PAYOUT_400_REQUIRED);

			verify(settlementRepository, never()).save(any());
		}

		@Test
		@DisplayName("현재 소유 구간이 없으면 ITEM_404 다 — 데이터 정합성이 깨진 상태다")
		void confirm_noCurrentOwnership_itemNotFound() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(seller);
			TradeOrder order = order(buyer, seller, item, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			order.deliver(LocalDateTime.now().plusHours(72));

			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(itemOwnershipRepository.findCurrent(ITEM_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> orderService.confirm(BUYER_ID, ORDER_ID))
					.isInstanceOf(CustomException.class)
					.extracting(OrderServiceTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_404);
		}
	}

	@Nested
	@DisplayName("자동 구매 확정 배치")
	class AutoConfirmDue {

		@Test
		@DisplayName("대상이 없으면 아무 일도 하지 않는다")
		void autoConfirmDue_noTargets_doesNothing() {
			LocalDateTime now = LocalDateTime.now();
			given(tradeOrderRepository.findAutoConfirmableIds(now)).willReturn(List.of());

			orderService.autoConfirmDue(now);

			verify(tradeOrderRepository, never()).findByIdForUpdate(any());
		}

		@Test
		@DisplayName("상태가 DELIVERED 가 아니게 된 건은 건너뛴다")
		void autoConfirmDue_statusChanged_skipped() {
			LocalDateTime now = LocalDateTime.now();
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			given(tradeOrderRepository.findAutoConfirmableIds(now)).willReturn(List.of(ORDER_ID));
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			orderService.autoConfirmDue(now);

			verify(settlementRepository, never()).save(any());
		}

		@Test
		@DisplayName("한 건이 정산 계좌 부재로 실패해도 나머지 배치는 계속 진행한다")
		void autoConfirmDue_oneFails_othersContinue() {
			LocalDateTime now = LocalDateTime.now();

			User sellerWithoutAccount = user(SELLER_ID);
			Item failingItem = item(sellerWithoutAccount);
			TradeOrder failing = order(user(BUYER_ID), sellerWithoutAccount, failingItem, OrderStatus.DELIVERED);
			failing.deliver(now.minusHours(1)); // 배치 대상이 되려면 autoConfirmAt 이 now 이전이어야 함
			ReflectionTestUtils.setField(failing, "id", 101L);

			User sellerWithAccount = user(5L);
			Item succeedingItem = item(sellerWithAccount);
			ReflectionTestUtils.setField(succeedingItem, "id", 201L);
			TradeOrder succeeding = order(user(4L), sellerWithAccount, succeedingItem, OrderStatus.DELIVERED);
			succeeding.deliver(now.minusHours(1));
			ReflectionTestUtils.setField(succeeding, "id", 102L);

			given(tradeOrderRepository.findAutoConfirmableIds(now)).willReturn(List.of(101L, 102L));
			given(tradeOrderRepository.findByIdForUpdate(101L)).willReturn(Optional.of(failing));
			given(tradeOrderRepository.findByIdForUpdate(102L)).willReturn(Optional.of(succeeding));
			given(itemOwnershipRepository.findCurrent(ITEM_ID))
					.willReturn(Optional.of(ItemOwnership.openInitial(failingItem, sellerWithoutAccount, LocalDate.of(2025, 2, 8))));
			given(itemOwnershipRepository.findCurrent(201L))
					.willReturn(Optional.of(ItemOwnership.openInitial(succeedingItem, sellerWithAccount, LocalDate.of(2025, 2, 8))));
			given(payoutAccountRepository.findAllOf(SELLER_ID)).willReturn(List.of());
			PayoutAccount account = PayoutAccount.register(sellerWithAccount, BankCode.TOSS_BANK, "김유진", "2222-2222", true);
			given(payoutAccountRepository.findAllOf(5L)).willReturn(List.of(account));
			given(businessDayCalculator.plusBusinessDays(any(), eq(2))).willReturn(LocalDate.of(2026, 6, 10));

			orderService.autoConfirmDue(now);

			assertThat(succeeding.getStatus()).isEqualTo(OrderStatus.PURCHASE_CONFIRMED);
			// 정산 계좌 조회를 상태 변경보다 먼저 하므로, 실패한 건은 아무 상태도 안 바뀐 채 남는다 —
			// 반쪽짜리 PURCHASE_CONFIRMED 가 생기지 않는다
			assertThat(failing.getStatus()).isEqualTo(OrderStatus.DELIVERED);
			verify(settlementRepository, times(1)).save(any());
		}
	}

	@Nested
	@DisplayName("거래 취소")
	class Cancel {

		@Test
		@DisplayName("구매자가 취소하면 환불하고 아이템을 미판매로 보낸다 — 판매중이었어도 재오픈하지 않는다")
		void cancel_byBuyer_refundsAndSetsNotForSale() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(seller);
			item.changeSaleStatus(SaleStatus.ON_SALE);
			TradeOrder order = order(buyer, seller, item, OrderStatus.PAYMENT_COMPLETED);
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			CancelResponse response = orderService.cancel(BUYER_ID, ORDER_ID);

			assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(item.getSaleStatus()).isEqualTo(SaleStatus.NOT_FOR_SALE);
			verify(paymentService).refund(eq(order.getOffer().getId()), any());
			verify(notificationService).notify(eq(seller), any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("판매자가 취소해도 같은 결과다")
		void cancel_bySeller_refundsAndSetsNotForSale() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(seller);
			TradeOrder order = order(buyer, seller, item, OrderStatus.PAYMENT_COMPLETED);
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			orderService.cancel(SELLER_ID, ORDER_ID);

			assertThat(item.getSaleStatus()).isEqualTo(SaleStatus.NOT_FOR_SALE);
			verify(notificationService).notify(eq(buyer), any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("관련 없는 사용자면 ORDER_403 이다")
		void cancel_unrelatedUser_forbidden() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			assertThatThrownBy(() -> orderService.cancel(OTHER_ID, ORDER_ID))
					.isInstanceOf(CustomException.class)
					.extracting(OrderServiceTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_403);

			verify(paymentService, never()).refund(any(), any());
		}

		@Test
		@DisplayName("상품 발송 이후에는 ORDER_409_CANCEL_WINDOW 다")
		void cancel_afterShipped_cancelWindowClosed() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			assertThatThrownBy(() -> orderService.cancel(BUYER_ID, ORDER_ID))
					.isInstanceOf(CustomException.class)
					.extracting(OrderServiceTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_409_CANCEL_WINDOW);

			verify(paymentService, never()).refund(any(), any());
		}
	}

	@Nested
	@DisplayName("정산 배치")
	class SettleDue {

		@Test
		@DisplayName("영업일이 지난 구매확정 건은 정산 완료로 넘긴다")
		void settleDue_dueDateReached_settles() {
			LocalDateTime now = LocalDateTime.of(2026, 6, 10, 1, 0);
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			order.deliver(now.minusDays(5));
			order.confirmPurchase(now.minusDays(3));
			Settlement settlement = Settlement.of(order, seller, BankCode.TOSS_BANK.name(), "1111-1111", "홍길동", 365000);

			given(settlementRepository.findAllBySettledAtIsNull()).willReturn(List.of(settlement));
			given(tradeOrderRepository.findByIdForUpdate(order.getId())).willReturn(Optional.of(order));
			given(businessDayCalculator.plusBusinessDays(any(), eq(2))).willReturn(LocalDate.of(2026, 6, 9));

			orderService.settleDue(now);

			assertThat(settlement.getSettledAt()).isNotNull();
			assertThat(order.getStatus()).isEqualTo(OrderStatus.SETTLED);
			verify(notificationService).notify(eq(seller), any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("영업일이 아직 안 지났으면 건너뛴다")
		void settleDue_dueDateNotReached_skipped() {
			LocalDateTime now = LocalDateTime.of(2026, 6, 10, 1, 0);
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			order.deliver(now.minusDays(5));
			order.confirmPurchase(now.minusDays(3));
			Settlement settlement = Settlement.of(order, seller, BankCode.TOSS_BANK.name(), "1111-1111", "홍길동", 365000);

			given(settlementRepository.findAllBySettledAtIsNull()).willReturn(List.of(settlement));
			given(tradeOrderRepository.findByIdForUpdate(order.getId())).willReturn(Optional.of(order));
			given(businessDayCalculator.plusBusinessDays(any(), eq(2))).willReturn(LocalDate.of(2026, 6, 11));

			orderService.settleDue(now);

			assertThat(settlement.getSettledAt()).isNull();
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PURCHASE_CONFIRMED);
		}

		@Test
		@DisplayName("거래 상태가 이미 구매확정이 아니면 건너뛴다")
		void settleDue_orderStatusChanged_skipped() {
			LocalDateTime now = LocalDateTime.of(2026, 6, 10, 1, 0);
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			// settlement 는 doConfirm() 이후에만 생기므로 confirmedAt 이 없는 상태는 실제로 도달하지
			// 않는다 — SETTLED 로 끝까지 전이시켜 '이미 처리된 거래' 를 현실적으로 재현한다
			TradeOrder order = order(buyer, seller, OrderStatus.SETTLED);
			Settlement settlement = Settlement.of(order, seller, BankCode.TOSS_BANK.name(), "1111-1111", "홍길동", 365000);

			given(settlementRepository.findAllBySettledAtIsNull()).willReturn(List.of(settlement));
			given(businessDayCalculator.plusBusinessDays(any(), eq(2))).willReturn(LocalDate.of(2026, 6, 10));
			given(tradeOrderRepository.findByIdForUpdate(order.getId())).willReturn(Optional.of(order));

			orderService.settleDue(now);

			assertThat(settlement.getSettledAt()).isNull();
			verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any(), any(), any());
		}
	}

	@Nested
	@DisplayName("판매 내역")
	class GetSales {

		@Test
		@DisplayName("대기중 탭은 PENDING 제안을 아이템 단위로 묶는다")
		void getSales_pendingTab_groupsByItem() {
			User seller = user(SELLER_ID);
			User buyer = user(BUYER_ID);
			Item item = item(seller);
			PurchaseOffer older = pendingOffer(item, buyer, seller, 10L, LocalDateTime.of(2026, 6, 1, 0, 0));
			PurchaseOffer newer = pendingOffer(item, buyer, seller, 11L, LocalDateTime.of(2026, 6, 2, 0, 0));
			given(purchaseOfferRepository.findAllPendingBySeller(SELLER_ID)).willReturn(List.of(newer, older));

			CursorSlice<OrderSalesRowResponse> slice =
					orderService.getSales(SELLER_ID, OrderFeedTab.PENDING, null, 20);

			assertThat(slice.getContent()).hasSize(1);
			OrderSalesRowResponse row = slice.getContent().get(0);
			assertThat(row.getRowType()).isEqualTo(OrderFeedRowType.OFFER);
			assertThat(row.getItemId()).isEqualTo(ITEM_ID);
			assertThat(row.getPendingOfferCount()).isEqualTo(2);
			assertThat(row.getOfferId()).isEqualTo(11L);
		}

		@Test
		@DisplayName("거래중 탭은 거래 레포지토리에 진행중 상태를 넘긴다")
		void getSales_inProgressTab_delegatesToRepository() {
			User seller = user(SELLER_ID);
			User buyer = user(BUYER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.SHIPPED);
			given(tradeOrderRepository.findAllBySellerForFeed(eq(SELLER_ID), any(), eq(null), eq(20)))
					.willReturn(List.of(order));

			CursorSlice<OrderSalesRowResponse> slice =
					orderService.getSales(SELLER_ID, OrderFeedTab.IN_PROGRESS, null, 20);

			assertThat(slice.getContent()).hasSize(1);
			assertThat(slice.getContent().get(0).getRowType()).isEqualTo(OrderFeedRowType.ORDER);
			assertThat(slice.getContent().get(0).getOrderId()).isEqualTo(order.getId());
		}

		@Test
		@DisplayName("전체 탭은 제안 묶음과 거래를 최신순으로 섞는다")
		void getSales_allTab_mergesByCreatedAtDesc() {
			User seller = user(SELLER_ID);
			User buyer = user(BUYER_ID);
			Item offerItem = item(seller);
			ReflectionTestUtils.setField(offerItem, "id", 210L);
			PurchaseOffer offer = pendingOffer(offerItem, buyer, seller, 10L, LocalDateTime.of(2026, 6, 5, 0, 0));

			TradeOrder olderOrder = order(buyer, seller, OrderStatus.SHIPPED);
			ReflectionTestUtils.setField(olderOrder, "createdAt", LocalDateTime.of(2026, 6, 1, 0, 0));

			given(purchaseOfferRepository.findAllPendingBySeller(SELLER_ID)).willReturn(List.of(offer));
			given(tradeOrderRepository.findAllBySellerForFeed(eq(SELLER_ID), eq(null), eq(null), eq(20)))
					.willReturn(List.of(olderOrder));

			CursorSlice<OrderSalesRowResponse> slice =
					orderService.getSales(SELLER_ID, OrderFeedTab.ALL, null, 20);

			assertThat(slice.getContent()).extracting(OrderSalesRowResponse::getRowType)
					.containsExactly(OrderFeedRowType.OFFER, OrderFeedRowType.ORDER);
		}

		@Test
		@DisplayName("size + 1 번째 행이 있으면 hasNext 가 true 다")
		void getSales_moreThanSize_hasNextTrue() {
			User seller = user(SELLER_ID);
			User buyer = user(BUYER_ID);
			TradeOrder first = order(buyer, seller, OrderStatus.SHIPPED);
			ReflectionTestUtils.setField(first, "id", 101L);
			TradeOrder second = order(buyer, seller, OrderStatus.SHIPPED);
			ReflectionTestUtils.setField(second, "id", 102L);
			given(tradeOrderRepository.findAllBySellerForFeed(eq(SELLER_ID), any(), eq(null), eq(1)))
					.willReturn(List.of(second, first));

			CursorSlice<OrderSalesRowResponse> slice =
					orderService.getSales(SELLER_ID, OrderFeedTab.DONE, null, 1);

			assertThat(slice.getContent()).hasSize(1);
			assertThat(slice.getHasNext()).isTrue();
			assertThat(slice.getNextCursor()).isNotNull();
		}
	}

	@Nested
	@DisplayName("구매 내역")
	class GetPurchases {

		@Test
		@DisplayName("대기중 탭은 아이템 단위로 묶지 않는다")
		void getPurchases_pendingTab_noGrouping() {
			User seller = user(SELLER_ID);
			User buyer = user(BUYER_ID);
			Item item = item(seller);
			PurchaseOffer offer = pendingOffer(item, buyer, seller, 10L, LocalDateTime.of(2026, 6, 1, 0, 0));
			given(purchaseOfferRepository.findAllPendingByBuyer(BUYER_ID)).willReturn(List.of(offer));

			CursorSlice<OrderPurchaseRowResponse> slice =
					orderService.getPurchases(BUYER_ID, OrderFeedTab.PENDING, null, 20);

			assertThat(slice.getContent()).hasSize(1);
			assertThat(slice.getContent().get(0).getOfferId()).isEqualTo(10L);
		}

		@Test
		@DisplayName("거래완료 탭은 거래 레포지토리에 완료 상태를 넘긴다")
		void getPurchases_doneTab_delegatesToRepository() {
			User seller = user(SELLER_ID);
			User buyer = user(BUYER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.SETTLED);
			given(tradeOrderRepository.findAllByBuyerForFeed(eq(BUYER_ID), any(), eq(null), eq(20)))
					.willReturn(List.of(order));

			CursorSlice<OrderPurchaseRowResponse> slice =
					orderService.getPurchases(BUYER_ID, OrderFeedTab.DONE, null, 20);

			assertThat(slice.getContent()).hasSize(1);
			assertThat(slice.getContent().get(0).getOrderStatus()).isEqualTo(OrderStatus.SETTLED);
		}
	}

	// ── 픽스처

	private User user(Long id) {
		User user = User.signUp("user" + id + "@tenure.app", "$2a$10$hashed", "홍길동",
				"821012345678", "닉네임" + id, null, Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private Item item(User owner) {
		Item item = Item.register(owner, null, "LVC 1955 501", "Levis", "L",
				WearTarget.MALE, "item/2026/09/a.jpg", LocalDate.of(2025, 2, 8));
		ReflectionTestUtils.setField(item, "id", ITEM_ID);
		return item;
	}

	private PurchaseOffer offer(User buyer, User seller, Item item) {
		ShippingAddress address = ShippingAddress.register(buyer, "홍길동", "010-1234-5678", "07027",
				"서울특별시 동작구 상도로 369", "104동 201호", null, null, true);
		PurchaseOffer offer = PurchaseOffer.create(
				null, item, buyer, seller, OfferType.PURCHASE_OFFER,
				360000, 5000, FeeBearer.SELLER, new BigDecimal("0.0300"),
				0, 10800, 375800, 365000, "직거래도 가능할까요?", address, LocalDateTime.now());
		ReflectionTestUtils.setField(offer, "id", OFFER_ID);
		return offer;
	}

	private TradeOrder order(User buyer, User seller, OrderStatus status) {
		return order(buyer, seller, item(seller), status);
	}

	/** createFromOffer 는 PAYMENT_COMPLETED 로 시작한다 — 목표 상태까지 실제 전이 메서드를 순서대로 밟는다. */
	private TradeOrder order(User buyer, User seller, Item item, OrderStatus status) {
		PurchaseOffer offer = offer(buyer, seller, item);
		TradeOrder order = TradeOrder.createFromOffer(offer, "2026042919502395", LocalDateTime.now());
		ReflectionTestUtils.setField(order, "id", ORDER_ID);
		// createdAt 은 JPA auditing 이 영속화 시점에 채운다 — 순수 객체라 기본값을 직접 둔다.
		// 커서 계산(Cursor.of)이 이 값을 쓰므로 비워 두면 NPE 가 난다
		ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now());

		LocalDateTime now = LocalDateTime.now();
		switch (status) {
			case SHIPPED -> order.ship();
			case DELIVERED -> {
				order.ship();
				order.deliver(now.plusHours(72));
			}
			case PURCHASE_CONFIRMED -> {
				order.ship();
				order.deliver(now.plusHours(72));
				order.confirmPurchase(now);
			}
			case SETTLED -> {
				order.ship();
				order.deliver(now.plusHours(72));
				order.confirmPurchase(now);
				order.settle();
			}
			case CANCELED -> order.cancel(now);
			case PAYMENT_COMPLETED -> { /* 초기 상태라 추가 전이가 없다 */ }
		}
		return order;
	}

	private OrderShipment shipmentOf(TradeOrder order) {
		return OrderShipment.fromOffer(order, order.getOffer());
	}

	private PurchaseOffer pendingOffer(Item item, User buyer, User seller, Long id, LocalDateTime createdAt) {
		PurchaseOffer offer = offer(buyer, seller, item);
		ReflectionTestUtils.setField(offer, "id", id);
		ReflectionTestUtils.setField(offer, "status", OfferStatus.PENDING);
		ReflectionTestUtils.setField(offer, "createdAt", createdAt);
		return offer;
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
