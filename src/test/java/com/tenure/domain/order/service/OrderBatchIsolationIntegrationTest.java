package com.tenure.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;

import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.address.repository.ShippingAddressRepository;
import com.tenure.domain.category.entity.Category;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.offer.repository.PurchaseOfferRepository;
import com.tenure.domain.order.entity.OrderShipment;
import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.order.repository.OrderShipmentRepository;
import com.tenure.domain.order.repository.OrderStatusHistoryRepository;
import com.tenure.domain.order.repository.TradeOrderRepository;
import com.tenure.domain.payout.entity.PayoutAccount;
import com.tenure.domain.payout.enums.BankCode;
import com.tenure.domain.payout.repository.PayoutAccountRepository;
import com.tenure.domain.settlement.repository.SettlementRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 배치 건별 트랜잭션 격리를 실제 트랜잭션 매니저로 검증한다.
 *
 * <p>Mockito 단위 테스트로는 이 격리를 재현할 수 없다 — 트랜잭션 매니저가 없어 notify() 가
 * 같은 트랜잭션에 참여하는 것도, 그 트랜잭션이 rollback-only 로 고정되는 것도 흉내 내지
 * 못한다. 그래서 여기서는 NotificationService 만 mock 으로 바꿔 끼우고(알림 전송 실패를
 * 흉내 내는 용도) 나머지는 실제 Spring 트랜잭션·실제 DB 로 돌린다.
 */
@DisplayName("자동 구매 확정 배치의 건별 트랜잭션 격리")
class OrderBatchIsolationIntegrationTest extends IntegrationTestSupport {

	/** V2 시드의 고정 id — 상의 · 긴소매 티셔츠 */
	private static final Long TOP_SHIRT = 116L;

	@Autowired private OrderService orderService;
	@Autowired private TradeOrderRepository tradeOrderRepository;
	@Autowired private OrderShipmentRepository orderShipmentRepository;
	@Autowired private OrderStatusHistoryRepository orderStatusHistoryRepository;
	@Autowired private SettlementRepository settlementRepository;
	@Autowired private PurchaseOfferRepository purchaseOfferRepository;
	@Autowired private ItemOwnershipRepository itemOwnershipRepository;
	@Autowired private ItemRepository itemRepository;
	@Autowired private ShippingAddressRepository shippingAddressRepository;
	@Autowired private PayoutAccountRepository payoutAccountRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private EntityManager em;

	@MockitoBean private NotificationService notificationService;

	private Fixture succeeding;
	private Fixture failing;

	private record Fixture(User buyer, User seller, Item item, TradeOrder order) {
	}

	@AfterEach
	void tearDown() {
		for (Fixture f : List.of(succeeding, failing)) {
			settlementRepository.findByOrderId(f.order().getId()).ifPresent(settlementRepository::delete);
			orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(f.order().getId())
					.forEach(orderStatusHistoryRepository::delete);
			orderShipmentRepository.findByOrderId(f.order().getId()).ifPresent(orderShipmentRepository::delete);
			itemOwnershipRepository.deleteAll(itemOwnershipRepository.findAllByItemId(f.item().getId()));
			tradeOrderRepository.delete(f.order());
			purchaseOfferRepository.findAll().stream()
					.filter(o -> o.getItem().getId().equals(f.item().getId()))
					.forEach(purchaseOfferRepository::delete);
			payoutAccountRepository.findAllOf(f.seller().getId()).forEach(payoutAccountRepository::delete);
			shippingAddressRepository.findAllOf(f.buyer().getId()).forEach(shippingAddressRepository::delete);
			itemRepository.delete(f.item());
			userRepository.delete(f.buyer());
			userRepository.delete(f.seller());
		}
	}

	@Test
	@DisplayName("한 건의 알림 발송이 실패해도 그 건만 롤백되고, 이미 처리한 다른 건의 커밋은 유지된다")
	void autoConfirmDue_oneNotifyFails_othersStayCommitted() {
		LocalDateTime now = LocalDateTime.now();
		succeeding = createDeliveredFixture("ok", now);
		failing = createDeliveredFixture("fail", now);

		// 실패 건을 향한 알림만 터지게 한다 — User 는 equals() 를 오버라이드하지 않아 참조
		// 동일성 비교다. REQUIRES_NEW 트랜잭션 안에서 새로 로드된 seller 는 다른 인스턴스라
		// eq(failing.seller()) 는 매칭되지 않는다 — targetId(주문 id)만으로 가려낸다
		willThrow(new RuntimeException("알림 전송 실패"))
				.given(notificationService)
				.notify(any(), any(), any(), any(), any(), any(), any(), eq(failing.order().getId()));

		orderService.autoConfirmDue(now);

		TradeOrder succeededReloaded = tradeOrderRepository.findById(succeeding.order().getId()).orElseThrow();
		assertThat(succeededReloaded.getStatus())
				.as("알림이 실패한 다른 건과 무관하게 이 건은 커밋되어 있어야 한다")
				.isEqualTo(OrderStatus.PURCHASE_CONFIRMED);
		assertThat(settlementRepository.findByOrderId(succeeding.order().getId()))
				.as("정산도 같은 트랜잭션에서 커밋됐어야 한다")
				.isPresent();
		assertThat(itemOwnershipRepository.findAllByItemId(succeeding.item().getId()))
				.as("소유권도 승계됐어야 한다")
				.hasSize(2);

		TradeOrder failedReloaded = tradeOrderRepository.findById(failing.order().getId()).orElseThrow();
		assertThat(failedReloaded.getStatus())
				.as("알림 발송 실패로 이 건의 트랜잭션은 롤백되어 상태가 그대로여야 한다")
				.isEqualTo(OrderStatus.DELIVERED);
		assertThat(settlementRepository.findByOrderId(failing.order().getId()))
				.as("정산도 같이 롤백되어 생기지 않았어야 한다")
				.isEmpty();
		assertThat(itemOwnershipRepository.findAllByItemId(failing.item().getId()))
				.as("소유권 승계도 롤백되어 최초 구간만 남아야 한다")
				.hasSize(1);
	}

	private Fixture createDeliveredFixture(String tag, LocalDateTime now) {
		String suffix = tag + "-" + UUID.randomUUID().toString().substring(0, 8);
		User seller = userRepository.save(User.signUp(
				"seller-%s@tenure.local".formatted(suffix), "$2a$10$hashed", "홍길동",
				"821012345678", "seller-%s".formatted(suffix), null, Gender.MALE, 170, 64));
		User buyer = userRepository.save(User.signUp(
				"buyer-%s@tenure.local".formatted(suffix), "$2a$10$hashed", "김유진",
				"821098765432", "buyer-%s".formatted(suffix), null, Gender.FEMALE, 160, 50));

		Category category = em.find(Category.class, TOP_SHIRT);
		Item item = itemRepository.save(Item.register(seller, category, "LVC 1955 501", "Levis", "L",
				WearTarget.UNISEX, "item/2026/09/a.jpg", LocalDate.of(2025, 2, 8)));
		itemOwnershipRepository.save(ItemOwnership.openInitial(item, seller, LocalDate.of(2025, 2, 8)));

		ShippingAddress address = shippingAddressRepository.save(ShippingAddress.register(
				buyer, "김유진", "010-9876-5432", "07027",
				"서울특별시 동작구 상도로 369", "104동 201호", null, null, true));
		payoutAccountRepository.save(PayoutAccount.register(seller, BankCode.TOSS_BANK, "홍길동", "1111-1111", true));

		PurchaseOffer offer = purchaseOfferRepository.save(PurchaseOffer.create(
				null, item, buyer, seller, OfferType.PURCHASE_OFFER,
				360_000, 5_000, FeeBearer.SELLER, new BigDecimal("0.0300"),
				0, 10_800, 375_800, 365_000, "직거래도 가능할까요?", address, LocalDateTime.now()));

		TradeOrder order = tradeOrderRepository.save(
				TradeOrder.createFromOffer(offer, "2026" + System.nanoTime(), now));
		orderShipmentRepository.save(OrderShipment.fromOffer(order, offer));

		order.ship();
		order.deliver(now.minusHours(1));
		order = tradeOrderRepository.save(order);

		return new Fixture(buyer, seller, item, order);
	}
}
