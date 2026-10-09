package com.tenure.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.order.exception.OrderErrorCode;
import com.tenure.domain.order.repository.OrderStatusHistoryRepository;
import com.tenure.domain.order.repository.TradeOrderRepository;
import com.tenure.domain.payout.entity.PayoutAccount;
import com.tenure.domain.payout.enums.BankCode;
import com.tenure.domain.payout.exception.PayoutErrorCode;
import com.tenure.domain.payout.repository.PayoutAccountRepository;
import com.tenure.domain.settlement.entity.Settlement;
import com.tenure.domain.settlement.repository.SettlementRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderBatchExecutor")
class OrderBatchExecutorTest {

	private static final Long BUYER_ID = 1L;
	private static final Long SELLER_ID = 2L;
	private static final Long OTHER_ID = 3L;
	private static final Long ORDER_ID = 100L;
	private static final Long ITEM_ID = 200L;
	private static final Long SETTLEMENT_ID = 900L;

	@Mock private TradeOrderRepository tradeOrderRepository;
	@Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
	@Mock private SettlementRepository settlementRepository;
	@Mock private ItemOwnershipRepository itemOwnershipRepository;
	@Mock private PayoutAccountRepository payoutAccountRepository;
	@Mock private NotificationService notificationService;

	private OrderBatchExecutor executor;

	@org.junit.jupiter.api.BeforeEach
	void setUp() {
		executor = new OrderBatchExecutor(
				tradeOrderRepository, orderStatusHistoryRepository, settlementRepository,
				itemOwnershipRepository, payoutAccountRepository, notificationService);
	}

	@Nested
	@DisplayName("구매자 요청 구매 확정")
	class ConfirmForBuyer {

		@Test
		@DisplayName("배송완료 거래를 확정하면 소유권을 승계하고 정산을 생성한다")
		void confirmForBuyer_delivered_transfersOwnershipAndCreatesSettlement() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(seller);
			TradeOrder order = order(buyer, seller, item, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			order.deliver(LocalDateTime.now().plusHours(72));
			ItemOwnership current = ItemOwnership.openInitial(item, seller, LocalDate.of(2025, 2, 8));
			PayoutAccount account = PayoutAccount.register(seller, BankCode.TOSS_BANK, "홍길동", "1111-1111", true);

			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(itemOwnershipRepository.findCurrent(ITEM_ID)).willReturn(Optional.of(current));
			given(payoutAccountRepository.findPrimaryOf(SELLER_ID)).willReturn(Optional.of(account));

			LocalDateTime now = LocalDateTime.now();
			LocalDate confirmedDate = executor.confirmForBuyer(ORDER_ID, BUYER_ID, now);

			assertThat(confirmedDate).isEqualTo(now.toLocalDate());
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PURCHASE_CONFIRMED);
			assertThat(current.isClosed()).isTrue();
			assertThat(item.getSaleStatus()).isEqualTo(SaleStatus.SOLD);
			verify(itemOwnershipRepository).save(any(ItemOwnership.class));
			verify(settlementRepository).save(any(Settlement.class));
			verify(notificationService).notify(
					org.mockito.ArgumentMatchers.eq(seller), any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("구매자가 아니면 ORDER_403 이다")
		void confirmForBuyer_notBuyer_forbidden() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			order.deliver(LocalDateTime.now().plusHours(72));
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			assertThatThrownBy(() -> executor.confirmForBuyer(ORDER_ID, OTHER_ID, LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.extracting(OrderBatchExecutorTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_403);
		}

		@Test
		@DisplayName("배송완료 전이면 ORDER_409_STATUS 다")
		void confirmForBuyer_notDelivered_conflict() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			assertThatThrownBy(() -> executor.confirmForBuyer(ORDER_ID, BUYER_ID, LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.extracting(OrderBatchExecutorTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_409_STATUS);
		}

		@Test
		@DisplayName("없는 거래면 ORDER_404 다")
		void confirmForBuyer_unknownOrder_notFound() {
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> executor.confirmForBuyer(ORDER_ID, BUYER_ID, LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.extracting(OrderBatchExecutorTest::errorCodeOf)
					.isEqualTo(OrderErrorCode.ORDER_404);
		}

		@Test
		@DisplayName("정산 계좌가 없으면 PAYOUT_400_REQUIRED 다 — 상태는 바뀌지 않은 채 남는다")
		void confirmForBuyer_noPayoutAccount_rejected() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(seller);
			TradeOrder order = order(buyer, seller, item, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			order.deliver(LocalDateTime.now().plusHours(72));
			ItemOwnership current = ItemOwnership.openInitial(item, seller, LocalDate.of(2025, 2, 8));

			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(itemOwnershipRepository.findCurrent(ITEM_ID)).willReturn(Optional.of(current));
			given(payoutAccountRepository.findPrimaryOf(SELLER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> executor.confirmForBuyer(ORDER_ID, BUYER_ID, LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.extracting(OrderBatchExecutorTest::errorCodeOf)
					.isEqualTo(PayoutErrorCode.PAYOUT_400_REQUIRED);

			assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
			verify(settlementRepository, never()).save(any());
		}

		@Test
		@DisplayName("현재 소유 구간이 없으면 ITEM_404 다 — 데이터 정합성이 깨진 상태다")
		void confirmForBuyer_noCurrentOwnership_itemNotFound() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(seller);
			TradeOrder order = order(buyer, seller, item, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			order.deliver(LocalDateTime.now().plusHours(72));

			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(itemOwnershipRepository.findCurrent(ITEM_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> executor.confirmForBuyer(ORDER_ID, BUYER_ID, LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.extracting(OrderBatchExecutorTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_404);
		}
	}

	@Nested
	@DisplayName("자동 구매 확정 건별 처리")
	class ConfirmIfDue {

		@Test
		@DisplayName("배송완료 +72h 지난 건은 확정 처리한다")
		void confirmIfDue_due_confirms() {
			LocalDateTime now = LocalDateTime.now();
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(seller);
			TradeOrder order = order(buyer, seller, item, OrderStatus.DELIVERED);
			order.deliver(now.minusHours(1));
			ItemOwnership current = ItemOwnership.openInitial(item, seller, LocalDate.of(2025, 2, 8));
			PayoutAccount account = PayoutAccount.register(seller, BankCode.TOSS_BANK, "홍길동", "1111-1111", true);

			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(itemOwnershipRepository.findCurrent(ITEM_ID)).willReturn(Optional.of(current));
			given(payoutAccountRepository.findPrimaryOf(SELLER_ID)).willReturn(Optional.of(account));

			executor.confirmIfDue(ORDER_ID, now);

			assertThat(order.getStatus()).isEqualTo(OrderStatus.PURCHASE_CONFIRMED);
			verify(settlementRepository).save(any(Settlement.class));
		}

		@Test
		@DisplayName("그 사이 상태가 DELIVERED 가 아니게 된 건은 건너뛴다")
		void confirmIfDue_statusChanged_skipped() {
			LocalDateTime now = LocalDateTime.now();
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			executor.confirmIfDue(ORDER_ID, now);

			verify(settlementRepository, never()).save(any());
		}

		@Test
		@DisplayName("자동 확정 시각이 아직 안 지났으면 건너뛴다")
		void confirmIfDue_notYetDue_skipped() {
			LocalDateTime now = LocalDateTime.now();
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			order.deliver(now.plusHours(72));
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			executor.confirmIfDue(ORDER_ID, now);

			verify(settlementRepository, never()).save(any());
		}

		@Test
		@DisplayName("거래가 이미 없으면 조용히 끝낸다")
		void confirmIfDue_unknownOrder_noop() {
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.empty());

			executor.confirmIfDue(ORDER_ID, LocalDateTime.now());

			verify(settlementRepository, never()).save(any());
		}
	}

	@Nested
	@DisplayName("정산 배치 건별 처리")
	class SettleIfDue {

		@Test
		@DisplayName("구매확정 건은 정산 완료로 넘긴다")
		void settleIfDue_confirmed_settles() {
			LocalDateTime now = LocalDateTime.now();
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			order.ship();
			order.deliver(now.minusDays(5));
			order.confirmPurchase(now.minusDays(3));
			Settlement settlement = Settlement.of(order, seller, BankCode.TOSS_BANK.name(), "1111-1111", "홍길동", 365000);

			given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			executor.settleIfDue(SETTLEMENT_ID, ORDER_ID, now);

			assertThat(settlement.getSettledAt()).isEqualTo(now);
			assertThat(order.getStatus()).isEqualTo(OrderStatus.SETTLED);
			verify(notificationService).notify(
					org.mockito.ArgumentMatchers.eq(seller), any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("정산 건이 이미 없으면(동시 처리 등) 건너뛴다")
		void settleIfDue_settlementMissing_skipped() {
			given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.empty());

			executor.settleIfDue(SETTLEMENT_ID, ORDER_ID, LocalDateTime.now());

			verify(tradeOrderRepository, never()).findByIdForUpdate(any());
		}

		@Test
		@DisplayName("이미 정산된 건은 다시 처리하지 않는다")
		void settleIfDue_alreadySettled_skipped() {
			User seller = user(SELLER_ID);
			User buyer = user(BUYER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			Settlement settlement = Settlement.of(order, seller, BankCode.TOSS_BANK.name(), "1111-1111", "홍길동", 365000);
			settlement.settle(LocalDateTime.now().minusDays(1));

			given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));

			executor.settleIfDue(SETTLEMENT_ID, ORDER_ID, LocalDateTime.now());

			verify(tradeOrderRepository, never()).findByIdForUpdate(any());
		}

		@Test
		@DisplayName("거래 상태가 구매확정이 아니면 건너뛴다")
		void settleIfDue_orderStatusChanged_skipped() {
			LocalDateTime now = LocalDateTime.now();
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.SETTLED);
			Settlement settlement = Settlement.of(order, seller, BankCode.TOSS_BANK.name(), "1111-1111", "홍길동", 365000);

			given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			executor.settleIfDue(SETTLEMENT_ID, ORDER_ID, now);

			assertThat(settlement.getSettledAt()).isNull();
			verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("거래가 이미 없으면 건너뛴다")
		void settleIfDue_orderMissing_skipped() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			TradeOrder order = order(buyer, seller, OrderStatus.PAYMENT_COMPLETED);
			Settlement settlement = Settlement.of(order, seller, BankCode.TOSS_BANK.name(), "1111-1111", "홍길동", 365000);

			given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
			given(tradeOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.empty());

			executor.settleIfDue(SETTLEMENT_ID, ORDER_ID, LocalDateTime.now());

			assertThat(settlement.getSettledAt()).isNull();
		}
	}

	// ── 픽스처 ──────────────────────────────────────────────────────

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
		ReflectionTestUtils.setField(offer, "id", 300L);
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

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
