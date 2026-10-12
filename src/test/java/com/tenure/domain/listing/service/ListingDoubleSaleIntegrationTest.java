package com.tenure.domain.listing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.address.repository.ShippingAddressRepository;
import com.tenure.domain.category.entity.Category;
import com.tenure.domain.category.enums.MeasurementField;
import com.tenure.domain.chat.repository.ChatRoomRepository;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.SaleStatus;
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
import com.tenure.domain.notification.repository.NotificationRepository;
import com.tenure.domain.offer.dto.request.OfferCreateRequest;
import com.tenure.domain.offer.dto.response.OfferCreateResponse;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.offer.repository.PurchaseOfferRepository;
import com.tenure.domain.offer.service.OfferService;
import com.tenure.domain.order.repository.OrderShipmentRepository;
import com.tenure.domain.order.repository.OrderStatusHistoryRepository;
import com.tenure.domain.order.repository.TradeOrderRepository;
import com.tenure.domain.payment.repository.PaymentRepository;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 판매 전환·제안 수락이 실제 Hibernate 세션에서도 "이중 판매"를 막는지 본다.
 *
 * <p>Mockito 단위 테스트(ListingServiceTest·OfferServiceTest)는 item 이 실제 영속성 컨텍스트에
 * 올라간 엔티티가 아니라 평범한 객체라, 벌크 {@code @Modifying(clearAutomatically = true)} 가
 * 영속성 컨텍스트를 비워 뒤따르는 변경이 detach 된 객체를 건드리는 문제를 흉내내지 못한다.
 * 아래 세 가지는 전부 실제 세션으로만 재현되고 고정된다 —
 * (1) {@code item.changeSaleStatus(ON_SALE)} 이 실측·상태이상·대표 OOTD 저장(벌크 삭제) 보다
 * 뒤에 있어 커밋되지 않던 것, (2) 판매 전환이 이미 수락된(진행 중인) 거래를 검사하지 않던 것,
 * (3) 제안 수락이 {@code offer.getListing()} 이 아니라 아이템의 실제 열린 등록을 닫아야 하는 것.
 */
@DisplayName("판매 전환·제안 수락 — 이중 판매 방지")
class ListingDoubleSaleIntegrationTest extends IntegrationTestSupport {

	/** V2 시드의 고정 id — 상의 · 긴소매 티셔츠 */
	private static final Long TOP_SHIRT = 116L;

	@Autowired private ListingService listingService;
	@Autowired private OfferService offerService;
	@Autowired private ItemRepository itemRepository;
	@Autowired private ItemOwnershipRepository itemOwnershipRepository;
	@Autowired private ItemMeasurementRepository itemMeasurementRepository;
	@Autowired private ItemConditionRepository itemConditionRepository;
	@Autowired private ItemListingRepository itemListingRepository;
	@Autowired private ListingFeaturedPostRepository listingFeaturedPostRepository;
	@Autowired private PayoutAccountRepository payoutAccountRepository;
	@Autowired private PostRepository postRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private ShippingAddressRepository shippingAddressRepository;
	@Autowired private PurchaseOfferRepository purchaseOfferRepository;
	@Autowired private PaymentRepository paymentRepository;
	@Autowired private ChatRoomRepository chatRoomRepository;
	@Autowired private TradeOrderRepository tradeOrderRepository;
	@Autowired private OrderShipmentRepository orderShipmentRepository;
	@Autowired private OrderStatusHistoryRepository orderStatusHistoryRepository;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private EntityManager em;

	private User seller;
	private User buyer;
	private Item item;
	private Post post;

	@BeforeEach
	void setUp() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		seller = persistUser("seller-%s@tenure.local".formatted(tag), "seller-%s".formatted(tag));
		buyer = persistUser("buyer-%s@tenure.local".formatted(tag), "buyer-%s".formatted(tag));

		Category category = em.find(Category.class, TOP_SHIRT);
		item = itemRepository.save(Item.register(seller, category, "LVC 1955 501", "Levis", "L",
				WearTarget.MALE, "item/2026/09/a.jpg", LocalDate.of(2025, 2, 8)));
		itemOwnershipRepository.save(ItemOwnership.openInitial(item, seller, LocalDate.of(2025, 2, 8)));

		payoutAccountRepository.save(
				PayoutAccount.register(seller, BankCode.TOSS_BANK, "판매자", "1000-2000-3000", true));
		post = postRepository.save(Post.publish(seller, LocalDate.of(2026, 5, 1), AspectRatio.RATIO_3_4, true));

		shippingAddressRepository.save(ShippingAddress.register(
				buyer, "김유진", "010-9876-5432", "07027",
				"서울특별시 동작구 상도로 369", "104동 201호", null, null, true));
	}

	@AfterEach
	void tearDown() {
		List<Long> orderIds = tradeOrderRepository.findAll().stream()
				.filter(o -> o.getItem().getId().equals(item.getId()))
				.map(o -> o.getId())
				.toList();

		// chat_room·payment 가 purchase_offer 를 RESTRICT 로 참조하므로 offer 보다 먼저 지운다
		chatRoomRepository.findByItemIdAndBuyerIdAndSellerId(item.getId(), buyer.getId(), seller.getId())
				.ifPresent(chatRoomRepository::delete);
		purchaseOfferRepository.findAll().stream()
				.filter(o -> o.getItem().getId().equals(item.getId()))
				.forEach(offer -> paymentRepository.findByOfferId(offer.getId()).ifPresent(paymentRepository::delete));

		orderIds.forEach(orderId -> {
			orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(orderId)
					.forEach(orderStatusHistoryRepository::delete);
			orderShipmentRepository.findByOrderId(orderId).ifPresent(orderShipmentRepository::delete);
		});
		tradeOrderRepository.deleteAllById(orderIds);

		purchaseOfferRepository.findAll().stream()
				.filter(o -> o.getItem().getId().equals(item.getId()))
				.forEach(purchaseOfferRepository::delete);

		List<com.tenure.domain.listing.entity.ItemListing> listings = itemListingRepository.findAll().stream()
				.filter(l -> l.getItem().getId().equals(item.getId()))
				.toList();
		listings.forEach(l -> listingFeaturedPostRepository.deleteAll(
				listingFeaturedPostRepository.findAllByListingId(l.getId())));
		itemListingRepository.deleteAll(listings);
		// 커스텀 @Modifying 쿼리는 활성 트랜잭션이 필요해 트랜잭션 없는 teardown 에서 못 쓴다 —
		// 조회 후 entity 기반 delete() 로 지운다(JpaRepository 기본 메서드는 자체 트랜잭션을 갖는다)
		itemMeasurementRepository.findByItemId(item.getId()).ifPresent(itemMeasurementRepository::delete);
		itemConditionRepository.findAllByItemId(item.getId()).forEach(itemConditionRepository::delete);
		itemOwnershipRepository.deleteAll(itemOwnershipRepository.findAllByItemId(item.getId()));
		postRepository.delete(post);
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
	@DisplayName("판매 전환 직후 item.sale_status 가 실제로 ON_SALE 로 커밋된다")
	void create_persistsOnSaleStatus() {
		listingService.create(seller.getId(), item.getId(), listingRequest(100_000));

		// 서비스 트랜잭션이 끝난 뒤 새로 읽어 — 커밋된 값인지 확인한다
		Item reloaded = itemRepository.findById(item.getId()).orElseThrow();
		assertThat(reloaded.getSaleStatus()).isEqualTo(SaleStatus.ON_SALE);
	}

	@Test
	@DisplayName("이미 수락되어 진행 중인 거래가 있는 아이템은 다시 판매 전환할 수 없다")
	void create_itemWithInProgressTrade_rejected() {
		listingService.create(seller.getId(), item.getId(), listingRequest(100_000));
		OfferCreateResponse offerResponse =
				offerService.create(buyer.getId(), item.getId(), tradeIntentRequest());
		offerService.accept(seller.getId(), offerResponse.getOfferId());

		// 구매 확정 전(거래가 아직 진행 중)이라 열린 등록은 없지만, 이미 팔린 아이템이다
		assertThat(itemListingRepository.findOpenByItemId(item.getId())).isEmpty();

		assertThatThrownBy(() -> listingService.create(
				seller.getId(), item.getId(), listingRequest(200_000)))
				.isInstanceOf(CustomException.class)
				.extracting(e -> ((CustomException) e).getErrorCode())
				.isEqualTo(ListingErrorCode.LISTING_409_IN_TRADE);
	}

	@Test
	@DisplayName("listing 없이 받은 제안을 나중에 열린 등록보다 먼저 수락해도 그 등록이 닫힌다")
	void accept_offerWithoutListing_closesLaterOpenedListing() {
		item.changeOfferAllowed(true);
		itemRepository.save(item);

		// 아이템이 아직 미판매일 때 구매 제안을 먼저 받음 — offer.listing 은 null
		OfferCreateResponse purchaseOfferResponse =
				offerService.create(buyer.getId(), item.getId(), purchaseOfferRequest(80_000));

		// 그 뒤에 판매 전환 — 새 등록이 열림
		listingService.create(seller.getId(), item.getId(), listingRequest(90_000));
		assertThat(itemListingRepository.findOpenByItemId(item.getId())).isPresent();

		// listing 이 아니라 그 전에 받은 구매 제안을 수락함
		offerService.accept(seller.getId(), purchaseOfferResponse.getOfferId());

		assertThat(itemListingRepository.findOpenByItemId(item.getId()))
				.as("판매 전환 때 열린 등록이 수락과 함께 닫혀야 한다")
				.isEmpty();
		assertThat(itemRepository.findById(item.getId()).orElseThrow().getSaleStatus())
				.isEqualTo(SaleStatus.SOLD);
	}

	private User persistUser(String email, String nickname) {
		return userRepository.save(User.signUp(email, "$2a$10$hashed", "홍길동",
				"821012345678", nickname, null, Gender.MALE, 170, 64));
	}

	private ListingCreateRequest listingRequest(int price) {
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

	private OfferCreateRequest tradeIntentRequest() {
		ShippingAddress address = shippingAddressRepository.findAllOf(buyer.getId()).getFirst();
		return OfferCreateRequest.builder()
				.offerType(OfferType.TRADE_INTENT)
				.shippingAddressId(address.getId())
				.paymentMethod(com.tenure.domain.payment.enums.PaymentMethod.TOSS_PAY)
				.paymentKey("test-payment-key")
				.orderId(UUID.randomUUID().toString())
				.build();
	}

	private OfferCreateRequest purchaseOfferRequest(int offerPrice) {
		ShippingAddress address = shippingAddressRepository.findAllOf(buyer.getId()).getFirst();
		return OfferCreateRequest.builder()
				.offerType(OfferType.PURCHASE_OFFER)
				.offerPrice(offerPrice)
				.shippingAddressId(address.getId())
				.paymentMethod(com.tenure.domain.payment.enums.PaymentMethod.TOSS_PAY)
				.paymentKey("test-payment-key")
				.orderId(UUID.randomUUID().toString())
				.build();
	}
}
