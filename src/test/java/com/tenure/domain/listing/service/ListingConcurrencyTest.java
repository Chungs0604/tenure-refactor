package com.tenure.domain.listing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.category.enums.MeasurementField;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.repository.ItemConditionRepository;
import com.tenure.domain.item.repository.ItemMeasurementRepository;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.listing.dto.request.ListingCreateRequest;
import com.tenure.domain.listing.dto.request.ListingItemRequest;
import com.tenure.domain.listing.exception.ListingErrorCode;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.listing.repository.ListingFeaturedPostRepository;
import com.tenure.domain.payout.entity.PayoutAccount;
import com.tenure.domain.payout.enums.BankCode;
import com.tenure.domain.payout.repository.PayoutAccountRepository;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.enums.AspectRatio;
import com.tenure.domain.post.repository.PostRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
 * 같은 아이템에 동시에 들어온 두 번의 판매 전환이 둘 다 성공하지 않는지 본다.
 *
 * <p>{@code findOwnedItem} 이 행 잠금 없이 조회하던 때는 "열린 등록 없음"·"진행 중 거래 없음"
 * 확인이 둘 다 커밋되지 않은 값을 보고 통과할 수 있어, 두 요청이 같은 아이템에 각자
 * {@code item_listing} 행을 열어버리는 TOCTOU 경쟁이 있었다. {@code findActiveForUpdate} 로
 * 바꿔 item 행을 먼저 잠그면 한쪽이 먼저 끝날 때까지 다른 쪽이 대기하다, 다시 확인했을 때
 * 커밋된 값(이미 열린 등록)을 보고 거부돼야 한다.
 *
 * <p>OfferConcurrencyTest 와 같은 이유로 트랜잭션을 테스트에 걸지 않는다.
 */
@DisplayName("판매 전환 동시성")
class ListingConcurrencyTest extends IntegrationTestSupport {

	/** V2 시드의 고정 id — 상의 · 긴소매 티셔츠 */
	private static final Long TOP_SHIRT = 116L;

	@Autowired private ListingService listingService;
	@Autowired private ItemRepository itemRepository;
	@Autowired private ItemOwnershipRepository itemOwnershipRepository;
	@Autowired private ItemMeasurementRepository itemMeasurementRepository;
	@Autowired private ItemConditionRepository itemConditionRepository;
	@Autowired private ItemListingRepository itemListingRepository;
	@Autowired private ListingFeaturedPostRepository listingFeaturedPostRepository;
	@Autowired private PayoutAccountRepository payoutAccountRepository;
	@Autowired private PostRepository postRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private EntityManager em;

	private User seller;
	private Item item;
	private Post post;

	@BeforeEach
	void setUp() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		seller = userRepository.save(User.signUp(
				"seller-%s@tenure.local".formatted(tag), "$2a$10$hashed", "홍길동",
				"821012345678", "seller-%s".formatted(tag), null, Gender.MALE, 170, 64));

		Category category = em.find(Category.class, TOP_SHIRT);
		item = itemRepository.save(Item.register(seller, category, "LVC 1955 501", "Levis", "L",
				WearTarget.MALE, "item/2026/09/a.jpg", LocalDate.of(2025, 2, 8)));
		itemOwnershipRepository.save(ItemOwnership.openInitial(item, seller, LocalDate.of(2025, 2, 8)));

		payoutAccountRepository.save(
				PayoutAccount.register(seller, BankCode.TOSS_BANK, "판매자", "1000-2000-3000", true));
		post = postRepository.save(Post.publish(seller, LocalDate.of(2026, 5, 1), AspectRatio.RATIO_3_4, true));
	}

	@AfterEach
	void tearDown() {
		List<com.tenure.domain.listing.entity.ItemListing> listings = itemListingRepository.findAll().stream()
				.filter(l -> l.getItem().getId().equals(item.getId()))
				.toList();
		listings.forEach(l -> listingFeaturedPostRepository.deleteAll(
				listingFeaturedPostRepository.findAllByListingId(l.getId())));
		itemListingRepository.deleteAll(listings);

		itemMeasurementRepository.findByItemId(item.getId()).ifPresent(itemMeasurementRepository::delete);
		itemConditionRepository.findAllByItemId(item.getId()).forEach(itemConditionRepository::delete);
		itemOwnershipRepository.deleteAll(itemOwnershipRepository.findAllByItemId(item.getId()));
		postRepository.delete(post);
		payoutAccountRepository.findAllOf(seller.getId()).forEach(payoutAccountRepository::delete);
		itemRepository.delete(item);
		userRepository.delete(seller);
	}

	@Test
	@DisplayName("같은 아이템을 동시에 두 번 판매 전환해도 한 건만 성공하고, 진 쪽은 LISTING_409_ALREADY_OPEN 이다")
	void create_concurrentSameItem_onlyOneSucceeds() throws Exception {
		Outcome outcome = runConcurrently(
				() -> listingService.create(seller.getId(), item.getId(), request(100_000)),
				() -> listingService.create(seller.getId(), item.getId(), request(200_000)));

		assertThat(outcome.succeeded()).as("성공한 요청 수").isEqualTo(1);
		assertThat(outcome.failures()).hasSize(1);
		assertThat(outcome.failures().getFirst())
				.isInstanceOf(CustomException.class)
				.extracting(e -> ((CustomException) e).getErrorCode())
				.isEqualTo(ListingErrorCode.LISTING_409_ALREADY_OPEN);

		long openRows = itemListingRepository.findAll().stream()
				.filter(l -> l.getItem().getId().equals(item.getId()) && l.isOpen())
				.count();
		assertThat(openRows).as("실제로 열린 판매 등록 행 수").isEqualTo(1);
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

	private ListingCreateRequest request(int price) {
		return ListingCreateRequest.builder()
				.item(ListingItemRequest.builder()
						.brandName("Levis")
						.name("LVC 1955 501")
						.categoryId(TOP_SHIRT)
						.sizeLabel("L")
						.wearTarget(WearTarget.MALE)
						.coverImageKey("item/2026/09/a.jpg")
						.build())
				.price(price)
				.featuredPostIds(List.of(post.getId()))
				.measurements(Map.of(
						MeasurementField.SHOULDER_WIDTH, BigDecimal.valueOf(45.0),
						MeasurementField.CHEST_WIDTH, BigDecimal.valueOf(52.0),
						MeasurementField.SLEEVE_LENGTH, BigDecimal.valueOf(61.0),
						MeasurementField.TOTAL_LENGTH, BigDecimal.valueOf(70.0)))
				.build();
	}

	private record Outcome(int succeeded, List<Throwable> failures) {
	}
}
