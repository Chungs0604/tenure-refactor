package com.tenure.domain.item.service;

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
import com.tenure.domain.offer.enums.OfferCancelReason;
import com.tenure.domain.offer.enums.OfferStatus;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.offer.repository.PurchaseOfferRepository;
import com.tenure.domain.offer.service.OfferService;
import com.tenure.domain.payment.entity.Payment;
import com.tenure.domain.payment.enums.PaymentMethod;
import com.tenure.domain.payment.enums.PaymentStatus;
import com.tenure.domain.payment.repository.PaymentRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 아이템 soft delete 가 남은 PENDING 구매 제안·결제 승인을 실제로 정리하는지 본다.
 *
 * <p>버그: isOnSale()·existsInProgress() 두 검사 모두 미판매 아이템에 걸린 PENDING 제안을
 * 못 잡아 delete() 가 그대로 통과했었다 — 결제는 AUTHORIZED, 제안은 PENDING 으로 방치됨.
 * OfferConcurrencyTest 와 같은 방식(IntegrationTestSupport, 트랜잭션 없이 각 테스트가 직접 데이터
 * 준비·정리)을 쓴다 — 서비스 메서드 자체가 각자 트랜잭션을 열고 커밋해야 잠금 재조회·결제 반영이
 * 실제로 의미를 가진다.
 */
@DisplayName("아이템 삭제가 남은 구매 제안을 정리하는지")
class ItemDeletePendingOfferIntegrationTest extends IntegrationTestSupport {

	/** V2 시드의 고정 id — 상의 · 긴소매 티셔츠 */
	private static final Long TOP_SHIRT = 116L;

	@Autowired private ItemService itemService;
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

		notificationRepository.deleteAll(notificationRepository.findAll().stream()
				.filter(n -> n.getUser().getId().equals(buyer.getId()) || n.getUser().getId().equals(seller.getId()))
				.toList());
		userRepository.delete(buyer);
		userRepository.delete(seller);
	}

	@Test
	@DisplayName("미판매 아이템을 삭제하면 PENDING 제안을 ITEM_DELETED 로 취소하고 결제 승인을 해제한다")
	void delete_releasesAuthorizedPaymentOfPendingOffer() {
		offerService.create(buyer.getId(), item.getId(), createRequest());
		PurchaseOffer offer = purchaseOfferRepository.findLatestByItemIdAndBuyerId(item.getId(), buyer.getId())
				.orElseThrow();
		Payment paymentBeforeDelete = paymentRepository.findByOfferId(offer.getId()).orElseThrow();
		assertThat(paymentBeforeDelete.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);

		itemService.delete(seller.getId(), item.getId());

		PurchaseOffer canceled = purchaseOfferRepository.findById(offer.getId()).orElseThrow();
		assertThat(canceled.getStatus()).isEqualTo(OfferStatus.CANCELED);
		assertThat(canceled.getCancelReason()).isEqualTo(OfferCancelReason.ITEM_DELETED);

		Payment released = paymentRepository.findByOfferId(offer.getId()).orElseThrow();
		assertThat(released.getStatus()).isEqualTo(PaymentStatus.RELEASED);

		Item deleted = itemRepository.findById(item.getId()).orElseThrow();
		assertThat(deleted.isDeleted()).isTrue();
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
}
