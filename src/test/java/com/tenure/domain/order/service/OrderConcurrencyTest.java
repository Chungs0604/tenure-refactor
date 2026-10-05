package com.tenure.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.address.repository.ShippingAddressRepository;
import com.tenure.domain.category.entity.Category;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.notification.repository.NotificationRepository;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.offer.repository.PurchaseOfferRepository;
import com.tenure.domain.order.entity.OrderShipment;
import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.order.exception.OrderErrorCode;
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
import com.tenure.global.exception.CustomException;
import com.tenure.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 구매자의 수동 구매확정과 배치의 자동 구매확정이 같은 거래를 동시에 처리하려 할 때를 본다.
 *
 * <p>둘 다 전제 조건이 DELIVERED 뿐이라 — 배송완료 +72h 가 지난 순간 구매자가 직접 확정 버튼을
 * 누르는 것과 배치 틱이 겹칠 수 있다. TradeOrderRepository.findByIdForUpdate 의 비관적 락으로
 * 직렬화되는지, 그 결과 소유권 승계·정산 생성이 정확히 한 번만 일어나는지가 핵심이다.
 *
 * <p>OfferConcurrencyTest 와 같은 이유로 트랜잭션을 테스트에 걸지 않는다.
 */
@DisplayName("구매확정 동시성")
class OrderConcurrencyTest extends IntegrationTestSupport {

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
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private EntityManager em;

	private User buyer;
	private User seller;
	private Item item;
	private TradeOrder order;

	@BeforeEach
	void setUp() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		seller = userRepository.save(User.signUp(
				"seller-%s@tenure.local".formatted(tag), "$2a$10$hashed", "홍길동",
				"821012345678", "seller-%s".formatted(tag), null, Gender.MALE, 170, 64));
		buyer = userRepository.save(User.signUp(
				"buyer-%s@tenure.local".formatted(tag), "$2a$10$hashed", "김유진",
				"821098765432", "buyer-%s".formatted(tag), null, Gender.FEMALE, 160, 50));

		Category category = em.find(Category.class, TOP_SHIRT);
		item = itemRepository.save(Item.register(seller, category, "LVC 1955 501", "Levis", "L",
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

		LocalDateTime now = LocalDateTime.now();
		order = tradeOrderRepository.save(TradeOrder.createFromOffer(offer, "2026" + System.nanoTime(), now));
		orderShipmentRepository.save(OrderShipment.fromOffer(order, offer));

		// 배송완료 +72h 가 이미 지난 상태로 직접 만든다 — 자동확정 배치 대상이면서 동시에
		// 구매자가 수동으로 눌러도 되는 상태
		order.ship();
		order.deliver(now.minusHours(1));
		order = tradeOrderRepository.save(order);
	}

	@AfterEach
	void tearDown() {
		settlementRepository.findByOrderId(order.getId()).ifPresent(settlementRepository::delete);
		orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(order.getId())
				.forEach(orderStatusHistoryRepository::delete);
		orderShipmentRepository.findByOrderId(order.getId()).ifPresent(orderShipmentRepository::delete);
		itemOwnershipRepository.deleteAll(itemOwnershipRepository.findAllByItemId(item.getId()));
		tradeOrderRepository.delete(order);
		purchaseOfferRepository.findAll().stream()
				.filter(o -> o.getItem().getId().equals(item.getId()))
				.forEach(purchaseOfferRepository::delete);
		payoutAccountRepository.findAllOf(seller.getId()).forEach(payoutAccountRepository::delete);
		shippingAddressRepository.findAllOf(buyer.getId()).forEach(shippingAddressRepository::delete);
		itemRepository.delete(item);

		notificationRepository.deleteAll(notificationRepository.findAll().stream()
				.filter(n -> n.getUser().getId().equals(buyer.getId()) || n.getUser().getId().equals(seller.getId()))
				.toList());
		userRepository.delete(buyer);
		userRepository.delete(seller);
	}

	@Test
	@DisplayName("구매자 수동 확정과 배치 자동 확정이 겹쳐도 소유권 승계·정산 생성은 정확히 한 번뿐이다")
	void confirm_racesWithAutoConfirmDue_onlyOneWins() throws Exception {
		LocalDateTime batchNow = LocalDateTime.now();

		Outcome outcome = runConcurrently(
				() -> orderService.confirm(buyer.getId(), order.getId()),
				() -> orderService.autoConfirmDue(batchNow));

		// 둘 다 예외 없이 끝나거나(배치는 내부에서 흡수), confirm 쪽이 져서 ORDER_409_STATUS 를
		// 던지는 경우만 허용한다 — 다른 예외는 버그다
		for (Throwable failure : outcome.failures()) {
			assertThat(failure).isInstanceOf(CustomException.class);
			assertThat(((CustomException) failure).getErrorCode()).isEqualTo(OrderErrorCode.ORDER_409_STATUS);
		}

		TradeOrder reloaded = tradeOrderRepository.findById(order.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PURCHASE_CONFIRMED);

		long settlementCount = settlementRepository.findByOrderId(order.getId()).isPresent() ? 1 : 0;
		assertThat(settlementCount).as("정산 레코드는 정확히 1건").isEqualTo(1);

		List<ItemOwnership> ownerships = itemOwnershipRepository.findAllByItemId(item.getId());
		assertThat(ownerships).as("소유 구간은 최초 1 + 승계 1 = 2건, 중복 승계가 없어야 한다").hasSize(2);
		assertThat(ownerships.stream().filter(o -> o.getOwner().getId().equals(buyer.getId())))
				.as("구매자 소유 구간은 정확히 1건").hasSize(1);
	}

	/** 두 작업을 같은 순간에 출발시키고 성공 수·실패(예외)를 모은다. OfferConcurrencyTest 와 같은 방식 */
	private Outcome runConcurrently(Runnable first, Runnable second) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(2);
		AtomicInteger succeeded = new AtomicInteger();
		List<Throwable> failures = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

		try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
			for (Runnable task : List.of(first, second)) {
				pool.submit(() -> {
					try {
						start.await();
						task.run();
						succeeded.incrementAndGet();
					} catch (Throwable e) {
						failures.add(e);
					} finally {
						done.countDown();
					}
				});
			}
			start.countDown();
			assertThat(done.await(10, TimeUnit.SECONDS)).as("10초 안에 끝난다").isTrue();
		}

		return new Outcome(succeeded.get(), List.copyOf(failures));
	}

	private record Outcome(int succeeded, List<Throwable> failures) {
	}
}
