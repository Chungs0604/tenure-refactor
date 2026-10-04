package com.tenure.domain.offer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.address.repository.ShippingAddressRepository;
import com.tenure.domain.category.entity.Category;
import com.tenure.domain.chat.repository.ChatRoomRepository;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.notification.repository.NotificationRepository;
import com.tenure.domain.offer.dto.request.OfferCreateRequest;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.offer.exception.OfferErrorCode;
import com.tenure.domain.offer.repository.PurchaseOfferRepository;
import com.tenure.domain.payment.enums.PaymentMethod;
import com.tenure.domain.payment.repository.PaymentRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
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
 * 미판매 구매 제안 1회 제한이 동시 요청에서도 지켜지는지 본다.
 *
 * <p>트랜잭션을 테스트에 걸지 않는다 — 걸면 두 호출이 한 트랜잭션을 공유해 검증 자체가
 * 사라진다. 그래서 롤백이 없고, 각 테스트가 자기 데이터를 직접 만든다. 같은 이유로
 * AddressConcurrencyTest 와 같은 방식(IntegrationTestSupport, CountDownLatch 로 동시 출발)을 쓴다.
 */
@DisplayName("구매 제안 1회 제한 동시성")
class OfferConcurrencyTest extends IntegrationTestSupport {

	/** V2 시드의 고정 id — 상의 · 긴소매 티셔츠 */
	private static final Long TOP_SHIRT = 116L;

	@Autowired private OfferService offerService;
	@Autowired private PurchaseOfferRepository purchaseOfferRepository;
	@Autowired private ShippingAddressRepository shippingAddressRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private ItemRepository itemRepository;
	@Autowired private ItemOwnershipRepository itemOwnershipRepository;
	@Autowired private ChatRoomRepository chatRoomRepository;
	@Autowired private PaymentRepository paymentRepository;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private EntityManager em;

	private User seller;
	private User buyer;
	private Item item;

	@BeforeEach
	void setUp() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		seller = persistUser("seller-%s@tenure.local".formatted(tag), "seller-%s".formatted(tag));
		buyer = persistUser("buyer-%s@tenure.local".formatted(tag), "buyer-%s".formatted(tag));

		item = persistItem(seller);
		persistShippingAddress(buyer);
	}

	/**
	 * 만든 걸 FK 순서대로 거꾸로 지운다 — payment·chat_room 이 purchase_offer 를 RESTRICT 로
	 * 참조해서, 먼저 안 지우면 뒤에서 purchase_offer 삭제가 막힌다
	 */
	@AfterEach
	void tearDown() {
		List<PurchaseOffer> offers = purchaseOfferRepository.findAll().stream()
				.filter(o -> o.getItem().getId().equals(item.getId()))
				.toList();

		offers.forEach(offer -> paymentRepository.findByOfferId(offer.getId())
				.ifPresent(paymentRepository::delete));
		chatRoomRepository.findByItemIdAndBuyerIdAndSellerId(item.getId(), buyer.getId(), seller.getId())
				.ifPresent(chatRoomRepository::delete);
		purchaseOfferRepository.deleteAll(offers);
		itemOwnershipRepository.deleteAll(itemOwnershipRepository.findAllByItemId(item.getId()));
		shippingAddressRepository.deleteAll(shippingAddressRepository.findAllOf(buyer.getId()));
		itemRepository.delete(item);

		// 제안 전송이 판매자에게 알림을 남겨 user 를 지우기 전에 같이 치워야 함
		notificationRepository.deleteAll(notificationRepository.findAll().stream()
				.filter(n -> n.getUser().getId().equals(buyer.getId()) || n.getUser().getId().equals(seller.getId()))
				.toList());
		userRepository.delete(buyer);
		userRepository.delete(seller);
	}

	@Test
	@DisplayName("같은 아이템에 동시에 두 번 제안해도 한 건만 성공하고, 진 쪽은 OFFER_409_ALREADY_USED 다")
	void create_concurrentSameItemSameBuyer_onlyOneSucceeds() throws Exception {
		Outcome outcome = runConcurrently(
				() -> offerService.create(buyer.getId(), item.getId(), createRequest()),
				() -> offerService.create(buyer.getId(), item.getId(), createRequest()));

		// DataIntegrityViolationException 이 그대로 새어나가 500 이 되지 않고,
		// 서비스가 OFFER_409_ALREADY_USED 로 번역해 올렸는지가 핵심임
		assertThat(outcome.succeeded()).as("성공한 요청 수").isEqualTo(1);
		assertThat(outcome.failures()).hasSize(1);
		assertThat(outcome.failures().getFirst())
				.isInstanceOf(CustomException.class)
				.extracting(e -> ((CustomException) e).getErrorCode())
				.isEqualTo(OfferErrorCode.OFFER_409_ALREADY_USED);

		// uk_purchase_offer_limit 가 실제로 중복 INSERT 자체를 막았는지 — 행이 1건만 남아야 함
		assertThat(purchaseOfferRepository.findLatestByItemIdAndBuyerId(item.getId(), buyer.getId()))
				.isPresent();
		long rows = purchaseOfferRepository.findAll().stream()
				.filter(o -> o.getItem().getId().equals(item.getId()) && o.getBuyer().getId().equals(buyer.getId()))
				.count();
		assertThat(rows).as("실제 저장된 제안 행 수").isEqualTo(1);
	}

	/** 두 작업을 같은 순간에 출발시키고 성공 수·실패(예외)를 모은다. */
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

	/**
	 * 리포지토리의 save() 로 만든다 — em.persist() 는 활성 트랜잭션이 있어야 하는데,
	 * 이 베이스(IntegrationTestSupport) 는 테스트에 트랜잭션을 걸지 않는다. save() 는 메서드
	 * 자체에 @Transactional 이 있어 트랜잭션 없이도 호출할 수 있다
	 */
	private User persistUser(String email, String nickname) {
		return userRepository.save(User.signUp(email, "$2a$10$hashed", "홍길동",
				"821012345678", nickname, null, Gender.MALE, 170, 64));
	}

	/** 미판매 + 구매 제안 허용 아이템. 현재 소유자(seller)의 소유 구간도 함께 엶 */
	private Item persistItem(User seller) {
		// 조회는 트랜잭션이 필요 없어 em.find 를 그대로 씀
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

	private OfferCreateRequest createRequest() {
		ShippingAddress address = shippingAddressRepository.findAllOf(buyer.getId()).getFirst();
		return OfferCreateRequest.builder()
				.offerType(OfferType.PURCHASE_OFFER)
				.offerPrice(100_000)
				.shippingAddressId(address.getId())
				.paymentMethod(PaymentMethod.TOSS_PAY)
				.build();
	}

	private record Outcome(int succeeded, List<Throwable> failures) {
	}
}
