package com.tenure.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.address.repository.ShippingAddressRepository;
import com.tenure.domain.category.entity.Category;
import com.tenure.domain.chat.repository.ChatRoomRepository;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.listing.service.ListingService;
import com.tenure.domain.notification.repository.NotificationRepository;
import com.tenure.domain.offer.dto.request.OfferCreateRequest;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.offer.enums.OfferCancelReason;
import com.tenure.domain.offer.enums.OfferStatus;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.offer.repository.PurchaseOfferRepository;
import com.tenure.domain.offer.service.OfferService;
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.order.repository.OrderShipmentRepository;
import com.tenure.domain.order.repository.OrderStatusHistoryRepository;
import com.tenure.domain.order.repository.TradeOrderRepository;
import com.tenure.domain.payment.enums.PaymentMethod;
import com.tenure.domain.payment.repository.PaymentRepository;
import com.tenure.domain.payout.entity.PayoutAccount;
import com.tenure.domain.payout.enums.BankCode;
import com.tenure.domain.payout.repository.PayoutAccountRepository;
import com.tenure.domain.user.dto.request.UserWithdrawRequest;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.enums.UserStatus;
import com.tenure.domain.user.enums.WithdrawalReason;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.domain.user.repository.UserWithdrawalRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 회원탈퇴 정리 루프가 상대의 동시 수락을 실제로 걸러내는지 본다.
 *
 * <p>트랜잭션을 테스트에 걸지 않는다 — OfferConcurrencyTest 와 같은 이유. 각 반복이 자기 데이터를
 * 직접 만들고 끝에 지운다. 한 번의 실행으로는 두 트랜잭션의 잠금 선점 순서가 매번 같게 나올 수
 * 있어 {@code @RepeatedTest}로 여러 번 돌려 양쪽 순서를 다 겪어보게 한다.
 */
@DisplayName("회원탈퇴·제안 수락 동시성")
class UserWithdrawalConcurrencyTest extends IntegrationTestSupport {

	/** V2 시드의 고정 id — 상의 · 긴소매 티셔츠 */
	private static final Long TOP_SHIRT = 116L;

	@Autowired private UserService userService;
	@Autowired private OfferService offerService;
	@Autowired private ListingService listingService;
	@Autowired private PurchaseOfferRepository purchaseOfferRepository;
	@Autowired private ShippingAddressRepository shippingAddressRepository;
	@Autowired private PayoutAccountRepository payoutAccountRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private UserWithdrawalRepository userWithdrawalRepository;
	@Autowired private ItemRepository itemRepository;
	@Autowired private ItemOwnershipRepository itemOwnershipRepository;
	@Autowired private ItemListingRepository itemListingRepository;
	@Autowired private ChatRoomRepository chatRoomRepository;
	@Autowired private PaymentRepository paymentRepository;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private TradeOrderRepository tradeOrderRepository;
	@Autowired private OrderShipmentRepository orderShipmentRepository;
	@Autowired private OrderStatusHistoryRepository orderStatusHistoryRepository;
	@Autowired private EntityManager em;

	@RepeatedTest(20)
	@DisplayName("탈퇴 정리와 제안 수락이 동시에 들어와도 '탈퇴한 계정 + 활성 거래'가 동시에 남지 않는다")
	void withdraw_concurrentWithAccept_neverLeavesActiveTradeOnWithdrawnAccount() throws Exception {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		User seller = persistUser("seller-%s@tenure.local".formatted(tag), "wseller-%s".formatted(tag));
		User buyer = persistUser("buyer-%s@tenure.local".formatted(tag), "wbuyer-%s".formatted(tag));
		Item item = persistItem(seller);
		persistShippingAddress(buyer);
		persistPayoutAccount(seller);

		Long offerId = offerService.create(buyer.getId(), item.getId(), createRequest(buyer)).getOfferId();

		AtomicReference<Throwable> withdrawFailure = new AtomicReference<>();
		AtomicReference<Throwable> acceptFailure = new AtomicReference<>();

		runConcurrently(
				() -> userService.withdraw(buyer.getId(),
						UserWithdrawRequest.builder().reason(WithdrawalReason.LOW_USAGE).build()),
				() -> offerService.accept(seller.getId(), offerId),
				withdrawFailure, acceptFailure);

		User buyerAfter = userRepository.findById(buyer.getId()).orElseThrow();
		boolean buyerWithdrawn = buyerAfter.getStatus() == UserStatus.WITHDRAWN;
		boolean activeTradeExists = tradeOrderRepository.existsInProgressByUser(buyer.getId(), OrderStatus.FINISHED);

		// 핵심 불변식 — 둘 다 참일 수는 없다. 둘 중 하나가 이겨야 한다
		assertThat(buyerWithdrawn && activeTradeExists)
				.as("탈퇴한 계정에 활성 거래가 남아있으면 안 됨 (withdraw 실패=%s, accept 실패=%s)",
						withdrawFailure.get(), acceptFailure.get())
				.isFalse();

		if (buyerWithdrawn) {
			// 탈퇴가 이겼으면 수락은 반드시 실패했어야 함(이미 취소된 제안)
			assertThat(acceptFailure.get()).as("탈퇴가 이겼으면 수락은 실패해야 함").isNotNull();
		} else {
			// 수락이 이겼으면 탈퇴는 반드시 실패했어야 함(USER_409_ACTIVE_TRADE)
			assertThat(withdrawFailure.get()).as("수락이 이겼으면 탈퇴는 실패해야 함").isInstanceOf(CustomException.class);
		}

		cleanUp(buyer, seller, item);
	}

	@RepeatedTest(20)
	@DisplayName("같은 사용자에게 탈퇴 요청이 동시에 두 번 들어오면 정확히 한쪽만 성공하고 진 쪽은 깔끔한 409 를 받는다")
	void withdraw_concurrentDoubleWithdraw_onlyOneSucceedsCleanly() throws Exception {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		User user = persistUser("dup-%s@tenure.local".formatted(tag), "wdup-%s".formatted(tag));

		AtomicReference<Throwable> failure1 = new AtomicReference<>();
		AtomicReference<Throwable> failure2 = new AtomicReference<>();

		runConcurrently(
				() -> userService.withdraw(user.getId(),
						UserWithdrawRequest.builder().reason(WithdrawalReason.LOW_USAGE).build()),
				() -> userService.withdraw(user.getId(),
						UserWithdrawRequest.builder().reason(WithdrawalReason.LOW_USAGE).build()),
				failure1, failure2);

		int succeeded = (failure1.get() == null ? 1 : 0) + (failure2.get() == null ? 1 : 0);
		assertThat(succeeded).as("정확히 한쪽만 성공해야 함 (failure1=%s, failure2=%s)", failure1.get(), failure2.get())
				.isEqualTo(1);

		Throwable loserFailure = failure1.get() != null ? failure1.get() : failure2.get();
		assertThat(loserFailure)
				.as("진 쪽은 CustomException(깔끔한 409)이어야 함 — 원인불명 예외가 그대로 새면 안 됨")
				.isInstanceOf(CustomException.class);

		long withdrawalRows = userWithdrawalRepository.findAll().stream()
				.filter(w -> w.getUser().getId().equals(user.getId()))
				.count();
		assertThat(withdrawalRows).as("탈퇴 사유 행은 1건만 남아야 함").isEqualTo(1);

		userWithdrawalRepository.findAll().stream()
				.filter(w -> w.getUser().getId().equals(user.getId()))
				.forEach(userWithdrawalRepository::delete);
		userRepository.delete(user);
	}

	@RepeatedTest(20)
	@DisplayName("탈퇴 정리와 판매 내림이 동시에 들어와도 교착되지 않고 제안은 정확히 한 사유로 취소된다")
	void withdraw_concurrentWithDelist_neverDeadlocksAndCancelsExactlyOnce() throws Exception {
		// 이 아이템은 seller 소유고 ON_SALE 이 아니라서(미판매 구매 제안), 탈퇴하는 buyer 의
		// closeOnSaleItemsForWithdrawal 는 대상이 없어 item 행을 전혀 건드리지 않는다 — 그래서
		// buyer 쪽은 offer 잠금만, seller 쪽(changeSaleStatus)은 item → offer 순으로 잠그는
		// 이 시나리오에서도 교착이 생기지 않아야 한다는 것이 이 테스트의 불변식이다. (withdraw 가
		// 자기 소유 ON_SALE 아이템을 정리할 때는 item → offer 순을 그대로 따른다 — 그 경로는
		// closeOnSaleItemsForWithdrawal 자체의 단위 테스트가 별도로 다룸)
		String tag = UUID.randomUUID().toString().substring(0, 8);
		User seller = persistUser("dseller-%s@tenure.local".formatted(tag), "dseller-%s".formatted(tag));
		User buyer = persistUser("dbuyer-%s@tenure.local".formatted(tag), "dbuyer-%s".formatted(tag));
		Item item = persistItem(seller);
		persistShippingAddress(buyer);

		Long offerId = offerService.create(buyer.getId(), item.getId(), createRequest(buyer)).getOfferId();

		AtomicReference<Throwable> withdrawFailure = new AtomicReference<>();
		AtomicReference<Throwable> delistFailure = new AtomicReference<>();

		runConcurrently(
				() -> userService.withdraw(buyer.getId(),
						UserWithdrawRequest.builder().reason(WithdrawalReason.LOW_USAGE).build()),
				() -> listingService.changeSaleStatus(seller.getId(), item.getId(), SaleStatus.NOT_FOR_SALE),
				withdrawFailure, delistFailure);

		assertThat(withdrawFailure.get()).as("탈퇴는 거래가 없어 항상 성공해야 함").isNull();
		assertThat(delistFailure.get()).as("판매 내림도 거래가 없어 항상 성공해야 함").isNull();

		User buyerAfter = userRepository.findById(buyer.getId()).orElseThrow();
		assertThat(buyerAfter.getStatus()).isEqualTo(UserStatus.WITHDRAWN);

		Item itemAfter = itemRepository.findById(item.getId()).orElseThrow();
		assertThat(itemAfter.getSaleStatus()).isEqualTo(SaleStatus.NOT_FOR_SALE);

		PurchaseOffer offerAfter = purchaseOfferRepository.findById(offerId).orElseThrow();
		assertThat(offerAfter.getStatus()).isEqualTo(OfferStatus.CANCELED);
		// 둘 다 결국 CANCELED 로 끝나지만, 둘 중 어느 쪽이 먼저 잠금을 얻었는지에 따라 사유가 갈린다 —
		// 두 사유 모두 정상이며, 한쪽이 처리한 뒤 다른 쪽은 재잠금에서 isPending()==false 로 조용히 넘어간다
		assertThat(offerAfter.getCancelReason())
				.isIn(OfferCancelReason.WITHDRAWN, OfferCancelReason.LISTING_DELISTED);

		cleanUp(buyer, seller, item);
	}

	@RepeatedTest(20)
	@DisplayName("서로에게 PENDING 제안을 보낸 두 사용자가 동시에 탈퇴해도 교착되지 않고 둘 다 성공한다")
	void withdraw_concurrentMutualOffers_neverDeadlocks() throws Exception {
		// U1 → U2 제안(offerA)과 U2 → U1 제안(offerB)이 동시에 PENDING 인 상태에서 둘이 동시에
		// 탈퇴하면, "내가 보낸 것부터" 따로 두 쿼리로 훑을 경우 한쪽은 offerA→offerB, 다른 쪽은
		// offerB→offerA 순으로 잠가 교착한다 — findPendingIdsByBuyerOrSellerId 로 하나의
		// id 오름차순 목록을 같이 쓰게 고친 것이 바로 이 레이스를 막기 위함이다
		String tag = UUID.randomUUID().toString().substring(0, 8);
		User user1 = persistUser("mutual1-%s@tenure.local".formatted(tag), "wmutual1-%s".formatted(tag));
		User user2 = persistUser("mutual2-%s@tenure.local".formatted(tag), "wmutual2-%s".formatted(tag));
		Item item1 = persistItem(user1);
		Item item2 = persistItem(user2);
		persistShippingAddress(user1);
		persistShippingAddress(user2);

		Long offerAId = offerService.create(user2.getId(), item1.getId(), createRequest(user2)).getOfferId();
		Long offerBId = offerService.create(user1.getId(), item2.getId(), createRequest(user1)).getOfferId();

		AtomicReference<Throwable> withdraw1Failure = new AtomicReference<>();
		AtomicReference<Throwable> withdraw2Failure = new AtomicReference<>();

		runConcurrently(
				() -> userService.withdraw(user1.getId(),
						UserWithdrawRequest.builder().reason(WithdrawalReason.LOW_USAGE).build()),
				() -> userService.withdraw(user2.getId(),
						UserWithdrawRequest.builder().reason(WithdrawalReason.LOW_USAGE).build()),
				withdraw1Failure, withdraw2Failure);

		assertThat(withdraw1Failure.get()).as("거래가 없어 둘 다 탈퇴에 성공해야 함(교착이면 여기서 예외가 샌다)").isNull();
		assertThat(withdraw2Failure.get()).as("거래가 없어 둘 다 탈퇴에 성공해야 함(교착이면 여기서 예외가 샌다)").isNull();

		assertThat(userRepository.findById(user1.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.WITHDRAWN);
		assertThat(userRepository.findById(user2.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.WITHDRAWN);

		PurchaseOffer offerA = purchaseOfferRepository.findById(offerAId).orElseThrow();
		PurchaseOffer offerB = purchaseOfferRepository.findById(offerBId).orElseThrow();
		assertThat(offerA.getStatus()).isEqualTo(OfferStatus.CANCELED);
		assertThat(offerA.getCancelReason()).isEqualTo(OfferCancelReason.WITHDRAWN);
		assertThat(offerB.getStatus()).isEqualTo(OfferStatus.CANCELED);
		assertThat(offerB.getCancelReason()).isEqualTo(OfferCancelReason.WITHDRAWN);

		cleanUpMutual(user1, user2, item1, item2);
	}

	@Test
	@DisplayName("탈퇴하면 내가 소유한 판매중 아이템도 미판매로 내려가고 열린 등록이 닫힌다")
	void withdraw_ownsOnSaleItem_closesItAndItsListing() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		User seller = persistUser("onsale-%s@tenure.local".formatted(tag), "wonsale-%s".formatted(tag));
		Item item = persistItem(seller);
		item.changeSaleStatus(SaleStatus.ON_SALE);
		item = itemRepository.save(item);
		ItemListing listing = itemListingRepository.save(
				ItemListing.open(item, seller, 320_000, 5_000, FeeBearer.SELLER, "설명", LocalDateTime.now()));

		userService.withdraw(seller.getId(), UserWithdrawRequest.builder().reason(WithdrawalReason.LOW_USAGE).build());

		Item itemAfter = itemRepository.findById(item.getId()).orElseThrow();
		assertThat(itemAfter.getSaleStatus()).isEqualTo(SaleStatus.NOT_FOR_SALE);
		ItemListing listingAfter = itemListingRepository.findById(listing.getId()).orElseThrow();
		assertThat(listingAfter.getClosedAt()).isNotNull();
		assertThat(userRepository.findById(seller.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.WITHDRAWN);

		itemListingRepository.delete(listingAfter);
		itemOwnershipRepository.deleteAll(itemOwnershipRepository.findAllByItemId(item.getId()));
		itemRepository.delete(itemAfter);
		userWithdrawalRepository.findAll().stream()
				.filter(w -> w.getUser().getId().equals(seller.getId()))
				.forEach(userWithdrawalRepository::delete);
		userRepository.delete(seller);
	}

	/** 두 작업을 같은 순간에 출발시키고 끝날 때까지 기다린다. 예외는 각자의 레퍼런스에 담는다. */
	private void runConcurrently(
			Runnable withdrawTask, Runnable acceptTask,
			AtomicReference<Throwable> withdrawFailure, AtomicReference<Throwable> acceptFailure
	) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(2);

		try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
			pool.submit(() -> {
				try {
					start.await();
					withdrawTask.run();
				} catch (Throwable e) {
					withdrawFailure.set(e);
				} finally {
					done.countDown();
				}
			});
			pool.submit(() -> {
				try {
					start.await();
					acceptTask.run();
				} catch (Throwable e) {
					acceptFailure.set(e);
				} finally {
					done.countDown();
				}
			});
			start.countDown();
			assertThat(done.await(10, TimeUnit.SECONDS)).as("10초 안에 끝난다").isTrue();
		}
	}

	/** FK 역순으로 지운다. accept() 가 이겼으면 trade_order·order_shipment·order_status_history 도 생겨 있음 */
	private void cleanUp(User buyer, User seller, Item item) {
		List<PurchaseOffer> offers = purchaseOfferRepository.findAll().stream()
				.filter(o -> o.getItem().getId().equals(item.getId()))
				.toList();

		tradeOrderRepository.findAll().stream()
				.filter(o -> o.getItem().getId().equals(item.getId()))
				.forEach(order -> {
					orderShipmentRepository.findByOrderId(order.getId()).ifPresent(orderShipmentRepository::delete);
					orderStatusHistoryRepository.deleteAll(
							orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(order.getId()));
				});

		offers.forEach(offer -> paymentRepository.findByOfferId(offer.getId())
				.ifPresent(paymentRepository::delete));

		tradeOrderRepository.findAll().stream()
				.filter(o -> o.getItem().getId().equals(item.getId()))
				.forEach(tradeOrderRepository::delete);

		chatRoomRepository.findByItemIdAndBuyerIdAndSellerId(item.getId(), buyer.getId(), seller.getId())
				.ifPresent(chatRoomRepository::delete);
		purchaseOfferRepository.deleteAll(offers);
		itemOwnershipRepository.deleteAll(itemOwnershipRepository.findAllByItemId(item.getId()));
		shippingAddressRepository.deleteAll(shippingAddressRepository.findAllOf(buyer.getId()));
		payoutAccountRepository.deleteAll(payoutAccountRepository.findAllOf(seller.getId()));
		itemRepository.delete(item);

		notificationRepository.deleteAll(notificationRepository.findAll().stream()
				.filter(n -> n.getUser().getId().equals(buyer.getId()) || n.getUser().getId().equals(seller.getId()))
				.toList());
		userWithdrawalRepository.findAll().stream()
				.filter(w -> w.getUser().getId().equals(buyer.getId()))
				.forEach(userWithdrawalRepository::delete);
		userRepository.delete(buyer);
		userRepository.delete(seller);
	}

	/** withdraw_concurrentMutualOffers_neverDeadlocks 전용 — 아이템 두 개, 유저 두 명, 서로 맞물린 제안 정리 */
	private void cleanUpMutual(User user1, User user2, Item item1, Item item2) {
		List<PurchaseOffer> offers = purchaseOfferRepository.findAll().stream()
				.filter(o -> o.getItem().getId().equals(item1.getId()) || o.getItem().getId().equals(item2.getId()))
				.toList();

		offers.forEach(offer -> paymentRepository.findByOfferId(offer.getId()).ifPresent(paymentRepository::delete));

		chatRoomRepository.findByItemIdAndBuyerIdAndSellerId(item1.getId(), user2.getId(), user1.getId())
				.ifPresent(chatRoomRepository::delete);
		chatRoomRepository.findByItemIdAndBuyerIdAndSellerId(item2.getId(), user1.getId(), user2.getId())
				.ifPresent(chatRoomRepository::delete);

		purchaseOfferRepository.deleteAll(offers);
		itemOwnershipRepository.deleteAll(itemOwnershipRepository.findAllByItemId(item1.getId()));
		itemOwnershipRepository.deleteAll(itemOwnershipRepository.findAllByItemId(item2.getId()));
		shippingAddressRepository.deleteAll(shippingAddressRepository.findAllOf(user1.getId()));
		shippingAddressRepository.deleteAll(shippingAddressRepository.findAllOf(user2.getId()));
		itemRepository.delete(item1);
		itemRepository.delete(item2);

		notificationRepository.deleteAll(notificationRepository.findAll().stream()
				.filter(n -> n.getUser().getId().equals(user1.getId()) || n.getUser().getId().equals(user2.getId()))
				.toList());
		userWithdrawalRepository.findAll().stream()
				.filter(w -> w.getUser().getId().equals(user1.getId()) || w.getUser().getId().equals(user2.getId()))
				.forEach(userWithdrawalRepository::delete);
		userRepository.delete(user1);
		userRepository.delete(user2);
	}

	private User persistUser(String email, String nickname) {
		return userRepository.save(User.signUp(email, "$2a$10$hashed", "홍길동",
				"821012345678", nickname, null, Gender.MALE, 170, 64));
	}

	private Item persistItem(User seller) {
		Category category = em.find(Category.class, TOP_SHIRT);
		Item created = Item.register(seller, category, "LVC 1955 501", "Levis", "L",
				WearTarget.UNISEX, "item/2026/09/a.jpg", LocalDate.of(2025, 2, 8));
		created.changeOfferAllowed(true);
		created = itemRepository.save(created);

		itemOwnershipRepository.save(ItemOwnership.openInitial(created, seller, LocalDate.of(2025, 2, 8)));
		return created;
	}

	private void persistShippingAddress(User owner) {
		ShippingAddress address = ShippingAddress.register(
				owner, "홍길동", "010-1234-5678", "07027",
				"서울특별시 동작구 상도로 369", "104동 201호", null, null, true);
		shippingAddressRepository.save(address);
	}

	/** 미판매 구매 제안 수락은 정산 계좌가 있어야 함 — 없으면 PAYOUT_400_REQUIRED 로 먼저 막힘 */
	private void persistPayoutAccount(User seller) {
		payoutAccountRepository.save(PayoutAccount.register(seller, BankCode.TOSS_BANK, "홍길동", "1111-1111", true));
	}

	private OfferCreateRequest createRequest(User buyer) {
		ShippingAddress address = shippingAddressRepository.findAllOf(buyer.getId()).getFirst();
		return OfferCreateRequest.builder()
				.offerType(OfferType.PURCHASE_OFFER)
				.offerPrice(100_000)
				.shippingAddressId(address.getId())
				.paymentMethod(PaymentMethod.TOSS_PAY)
				.paymentKey("test-payment-key")
				.orderId(UUID.randomUUID().toString())
				.build();
	}
}
