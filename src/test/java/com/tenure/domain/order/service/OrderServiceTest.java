package com.tenure.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.item.enums.WearTarget;
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
import com.tenure.domain.payout.enums.BankCode;
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
	@Mock private NotificationService notificationService;
	@Mock private FileStorage fileStorage;
	@Mock private BusinessDayCalculator businessDayCalculator;
	@Mock private OrderBatchExecutor orderBatchExecutor;

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

		/**
		 * 소유권 승계·정산 생성 같은 실제 업무 로직과 403/409/정산 계좌 없음 같은 검증은
		 * OrderBatchExecutor.confirmForBuyer 로 옮겼다 — OrderBatchExecutorTest 참고.
		 * 여기서는 OrderService.confirm() 이 그 결과를 받아 응답을 올바르게 조립하는지만 본다.
		 */
		@Test
		@DisplayName("배치 실행기가 돌려준 확정일로 정산 예정일을 계산해 응답한다")
		void confirm_delegatesToBatchExecutor_buildsResponse() {
			LocalDate confirmedDate = LocalDate.of(2026, 6, 8);
			LocalDate scheduledAt = LocalDate.of(2026, 6, 10);
			given(orderBatchExecutor.confirmForBuyer(eq(ORDER_ID), eq(BUYER_ID), any())).willReturn(confirmedDate);
			given(businessDayCalculator.plusBusinessDays(confirmedDate, 2)).willReturn(scheduledAt);

			ConfirmResponse response = orderService.confirm(BUYER_ID, ORDER_ID);

			assertThat(response.getStatus()).isEqualTo(OrderStatus.PURCHASE_CONFIRMED);
			assertThat(response.getSettlementScheduledAt()).isEqualTo(scheduledAt);
		}

		@Test
		@DisplayName("배치 실행기가 던진 예외를 그대로 전파한다")
		void confirm_batchExecutorThrows_propagates() {
			given(orderBatchExecutor.confirmForBuyer(eq(ORDER_ID), eq(OTHER_ID), any()))
					.willThrow(new CustomException(OrderErrorCode.ORDER_403));

			assertThatThrownBy(() -> orderService.confirm(OTHER_ID, ORDER_ID))
					.isInstanceOf(CustomException.class)
					.extracting(OrderServiceTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_403);

			verify(businessDayCalculator, never()).plusBusinessDays(any(), anyInt());
		}
	}

	/**
	 * DELIVERED 상태 재확인·소유권 승계 등 건별 업무 로직은 OrderBatchExecutor.confirmIfDue 로
	 * 옮겼다 — OrderBatchExecutorTest 참고. 여기서는 대상 id마다 그 메서드가 독립적으로(건별
	 * REQUIRES_NEW 트랜잭션으로) 호출되고, 한 건이 예외를 던져도 나머지는 계속 진행하는지만 본다.
	 */
	@Nested
	@DisplayName("자동 구매 확정 배치")
	class AutoConfirmDue {

		@Test
		@DisplayName("대상이 없으면 배치 실행기를 호출하지 않는다")
		void autoConfirmDue_noTargets_doesNothing() {
			LocalDateTime now = LocalDateTime.now();
			given(tradeOrderRepository.findAutoConfirmableIds(now)).willReturn(List.of());

			orderService.autoConfirmDue(now);

			verify(orderBatchExecutor, never()).confirmIfDue(any(), any());
		}

		@Test
		@DisplayName("대상 id마다 배치 실행기를 호출한다")
		void autoConfirmDue_targets_delegatesEachId() {
			LocalDateTime now = LocalDateTime.now();
			given(tradeOrderRepository.findAutoConfirmableIds(now)).willReturn(List.of(101L, 102L));

			orderService.autoConfirmDue(now);

			verify(orderBatchExecutor).confirmIfDue(101L, now);
			verify(orderBatchExecutor).confirmIfDue(102L, now);
		}

		@Test
		@DisplayName("한 건이 예외를 던져도 나머지 건은 계속 처리한다")
		void autoConfirmDue_oneThrows_othersContinue() {
			LocalDateTime now = LocalDateTime.now();
			given(tradeOrderRepository.findAutoConfirmableIds(now)).willReturn(List.of(101L, 102L));
			doThrow(new RuntimeException("정산 계좌 없음")).when(orderBatchExecutor).confirmIfDue(101L, now);

			orderService.autoConfirmDue(now);

			verify(orderBatchExecutor).confirmIfDue(101L, now);
			verify(orderBatchExecutor).confirmIfDue(102L, now);
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

	/**
	 * settlement·order 상태를 실제로 바꾸는 로직은 OrderBatchExecutor.settleIfDue 로 옮겼다 —
	 * OrderBatchExecutorTest 참고. 여기서는 영업일 계산(행 잠금 전에 끝내야 함)에 따라
	 * settleIfDue 호출 여부가 갈리는지, 한 건 예외가 나머지에 영향을 안 주는지만 본다.
	 */
	@Nested
	@DisplayName("정산 배치")
	class SettleDue {

		@Test
		@DisplayName("영업일이 지난 건은 배치 실행기에 위임한다")
		void settleDue_dueDateReached_delegatesToBatchExecutor() {
			LocalDateTime now = LocalDateTime.of(2026, 6, 10, 1, 0);
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			order.deliver(now.minusDays(5));
			order.confirmPurchase(now.minusDays(3));
			Settlement settlement = Settlement.of(order, seller, BankCode.TOSS_BANK.name(), "1111-1111", "홍길동", 365000);
			ReflectionTestUtils.setField(settlement, "id", 500L);

			given(settlementRepository.findAllBySettledAtIsNull()).willReturn(List.of(settlement));
			given(businessDayCalculator.plusBusinessDays(any(), eq(2))).willReturn(LocalDate.of(2026, 6, 9));

			orderService.settleDue(now);

			verify(orderBatchExecutor).settleIfDue(500L, ORDER_ID, now);
		}

		@Test
		@DisplayName("영업일이 아직 안 지났으면 배치 실행기를 호출하지 않는다")
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
			given(businessDayCalculator.plusBusinessDays(any(), eq(2))).willReturn(LocalDate.of(2026, 6, 11));

			orderService.settleDue(now);

			verify(orderBatchExecutor, never()).settleIfDue(any(), any(), any());
		}

		@Test
		@DisplayName("한 건이 예외를 던져도 나머지 건은 계속 처리한다")
		void settleDue_oneThrows_othersContinue() {
			LocalDateTime now = LocalDateTime.of(2026, 6, 10, 1, 0);
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order1 = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			order1.ship();
			order1.deliver(now.minusDays(5));
			order1.confirmPurchase(now.minusDays(3));
			Settlement settlement1 = Settlement.of(order1, seller, BankCode.TOSS_BANK.name(), "1111-1111", "홍길동", 365000);
			ReflectionTestUtils.setField(settlement1, "id", 501L);

			User buyer2 = user(4L);
			User seller2 = user(5L);
			Item item2 = item(seller2);
			ReflectionTestUtils.setField(item2, "id", 201L);
			TradeOrder order2 = order(buyer2, seller2, item2, OrderStatus.PAYMENT_COMPLETED);
			ReflectionTestUtils.setField(order2, "id", 102L);
			order2.ship();
			order2.deliver(now.minusDays(5));
			order2.confirmPurchase(now.minusDays(3));
			Settlement settlement2 = Settlement.of(order2, seller2, BankCode.TOSS_BANK.name(), "2222-2222", "김유진", 100000);
			ReflectionTestUtils.setField(settlement2, "id", 502L);

			given(settlementRepository.findAllBySettledAtIsNull()).willReturn(List.of(settlement1, settlement2));
			given(businessDayCalculator.plusBusinessDays(any(), eq(2))).willReturn(LocalDate.of(2026, 6, 9));
			doThrow(new RuntimeException("디비 오류")).when(orderBatchExecutor).settleIfDue(501L, ORDER_ID, now);

			orderService.settleDue(now);

			verify(orderBatchExecutor).settleIfDue(501L, ORDER_ID, now);
			verify(orderBatchExecutor).settleIfDue(502L, 102L, now);
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
