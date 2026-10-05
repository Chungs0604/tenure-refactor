package com.tenure.domain.offer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.category.entity.Category;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.offer.enums.OfferCancelReason;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.global.pagination.Cursor;
import com.tenure.support.RepositorySliceTestSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 구매 제안 조회 쿼리.
 *
 * H2 가 아니라 실제 MySQL 에서 돈다. 판매 내역 대기중 탭(전수 조회)과 구매 내역 전체 탭
 * (DB 커서 페이지네이션)의 경계가 다르므로 둘 다 확인해 둔다.
 */
@DisplayName("구매 제안 조회 쿼리")
class PurchaseOfferRepositoryTest extends RepositorySliceTestSupport {

	/** V2 시드의 고정 id — 상의 · 긴소매 티셔츠 */
	private static final Long TOP_SHIRT = 116L;

	@Autowired private EntityManager em;
	@Autowired private PurchaseOfferRepository purchaseOfferRepository;

	private User buyer;
	private User seller;

	@BeforeEach
	void setUp() {
		buyer = persistUser("buyer@tenure.app", "Buyer");
		seller = persistUser("seller@tenure.app", "Seller");
	}

	@Nested
	@DisplayName("판매 내역 대기중 — 전수 조회")
	class PendingForSeller {

		@Test
		@DisplayName("PENDING 만 담고 생성일 최신순이다")
		void findAllPendingBySeller_onlyPendingOrderedDesc() {
			PurchaseOffer older = persistOffer(persistItem(), buyer, seller);
			PurchaseOffer newer = persistOffer(persistItem(), buyer, seller);
			PurchaseOffer accepted = persistOffer(persistItem(), buyer, seller);
			accepted.accept(LocalDateTime.now());
			em.flush();

			List<PurchaseOffer> pending = purchaseOfferRepository.findAllPendingBySeller(seller.getId());

			assertThat(pending).extracting(PurchaseOffer::getId)
					.containsExactly(newer.getId(), older.getId());
		}

		@Test
		@DisplayName("남의 아이템에 걸린 제안은 담기지 않는다")
		void findAllPendingBySeller_othersOffer_excluded() {
			User otherSeller = persistUser("other-seller@tenure.app", "OtherSeller");
			persistOffer(persistItemFor(otherSeller), buyer, otherSeller);

			assertThat(purchaseOfferRepository.findAllPendingBySeller(seller.getId())).isEmpty();
		}
	}

	@Nested
	@DisplayName("구매 내역 대기중 — 전수 조회")
	class PendingForBuyer {

		@Test
		@DisplayName("PENDING 만 담고 생성일 최신순이다")
		void findAllPendingByBuyer_onlyPendingOrderedDesc() {
			PurchaseOffer older = persistOffer(persistItem(), buyer, seller);
			PurchaseOffer newer = persistOffer(persistItem(), buyer, seller);
			PurchaseOffer canceled = persistOffer(persistItem(), buyer, seller);
			canceled.cancel(OfferCancelReason.BUYER_CANCELED, LocalDateTime.now());
			em.flush();

			List<PurchaseOffer> pending = purchaseOfferRepository.findAllPendingByBuyer(buyer.getId());

			assertThat(pending).extracting(PurchaseOffer::getId)
					.containsExactly(newer.getId(), older.getId());
		}
	}

	@Nested
	@DisplayName("구매 내역 전체 탭 — 커서 페이지네이션")
	class FeedCursor {

		@Test
		@DisplayName("상태 무관하게 전체 이력을 최신순으로 담는다")
		void findAllByBuyerForFeed_includesAllStatuses() {
			PurchaseOffer pending = persistOffer(persistItem(), buyer, seller);
			PurchaseOffer rejected = persistOffer(persistItem(), buyer, seller);
			rejected.reject(LocalDateTime.now());
			em.flush();

			List<PurchaseOffer> all = purchaseOfferRepository.findAllByBuyerForFeed(buyer.getId(), null, 20);

			assertThat(all).extracting(PurchaseOffer::getId)
					.containsExactly(rejected.getId(), pending.getId());
		}

		@Test
		@DisplayName("size + 1 건까지 읽는다 — 다음 페이지 유무를 세지 않고 안다")
		void findAllByBuyerForFeed_readsOneMoreThanSize() {
			persistOffer(persistItem(), buyer, seller);
			persistOffer(persistItem(), buyer, seller);
			persistOffer(persistItem(), buyer, seller);

			assertThat(purchaseOfferRepository.findAllByBuyerForFeed(buyer.getId(), null, 2)).hasSize(3);
		}

		@Test
		@DisplayName("커서를 주면 그 뒤부터 가져온다")
		void findAllByBuyerForFeed_withCursor_returnsRowsAfterIt() {
			PurchaseOffer first = persistOffer(persistItem(), buyer, seller);
			PurchaseOffer second = persistOffer(persistItem(), buyer, seller);
			em.clear();

			PurchaseOffer lastOfFirstPage = purchaseOfferRepository.findAllByBuyerForFeed(buyer.getId(), null, 1).get(0);
			Cursor cursor = Cursor.of(lastOfFirstPage.getCreatedAt(), lastOfFirstPage.getId());

			List<PurchaseOffer> next = purchaseOfferRepository.findAllByBuyerForFeed(buyer.getId(), cursor, 20);

			assertThat(lastOfFirstPage.getId()).isEqualTo(second.getId());
			assertThat(next).extracting(PurchaseOffer::getId).containsExactly(first.getId());
		}

		@Test
		@DisplayName("생성일이 같아도 id 로 갈라 건너뛰지 않는다")
		void findAllByBuyerForFeed_sameCreatedAt_tieBreaksById() {
			PurchaseOffer first = persistOffer(persistItem(), buyer, seller);
			PurchaseOffer second = persistOffer(persistItem(), buyer, seller);
			LocalDateTime sameMoment = LocalDateTime.now().withNano(0);
			backdateCreatedAt(first, sameMoment);
			backdateCreatedAt(second, sameMoment);

			List<PurchaseOffer> firstPage = purchaseOfferRepository.findAllByBuyerForFeed(buyer.getId(), null, 1);
			PurchaseOffer head = firstPage.get(0);
			List<PurchaseOffer> next = purchaseOfferRepository.findAllByBuyerForFeed(
					buyer.getId(), Cursor.of(head.getCreatedAt(), head.getId()), 20);

			assertThat(head.getId()).isEqualTo(second.getId());
			assertThat(next).extracting(PurchaseOffer::getId).containsExactly(first.getId());
		}

		@Test
		@DisplayName("남의 제안은 담기지 않는다")
		void findAllByBuyerForFeed_othersOffer_excluded() {
			User otherBuyer = persistUser("other-buyer@tenure.app", "OtherBuyer");
			persistOffer(persistItem(), otherBuyer, seller);

			assertThat(purchaseOfferRepository.findAllByBuyerForFeed(buyer.getId(), null, 20)).isEmpty();
		}
	}

	// ── 픽스처

	private User persistUser(String email, String nickname) {
		User user = User.signUp(email, "$2a$10$hashed", "홍길동",
				"821012345678", nickname, null, Gender.MALE, 170, 64);
		em.persist(user);
		em.flush();
		return user;
	}

	private Item persistItem() {
		return persistItemFor(seller);
	}

	private Item persistItemFor(User owner) {
		Category category = em.find(Category.class, TOP_SHIRT);
		Item item = Item.register(owner, category, "LVC 1955 501", "Levis", "L",
				WearTarget.UNISEX, "item/2026/09/a.jpg", LocalDate.of(2025, 2, 8));
		em.persist(item);
		em.flush();
		return item;
	}

	private PurchaseOffer persistOffer(Item item, User offerBuyer, User offerSeller) {
		// 기본 배송지는 사용자당 1개만 허용된다(uk_shipping_address_default) — 같은 구매자로
		// 여러 제안을 만들 때마다 기본으로 등록하면 두 번째부터 충돌한다
		ShippingAddress address = ShippingAddress.register(offerBuyer, "홍길동", "010-1234-5678", "07027",
				"서울특별시 동작구 상도로 369", "104동 201호", null, null, false);
		em.persist(address);
		PurchaseOffer offer = PurchaseOffer.create(
				null, item, offerBuyer, offerSeller, OfferType.PURCHASE_OFFER,
				360000, 5000, FeeBearer.SELLER, new BigDecimal("0.0300"),
				0, 10800, 375800, 365000, "직거래도 가능할까요?", address, LocalDateTime.now());
		em.persist(offer);
		em.flush();
		return offer;
	}

	/** 생성일은 감사 컬럼이라 엔티티로는 과거로 돌릴 수 없다. */
	private void backdateCreatedAt(PurchaseOffer offer, LocalDateTime createdAt) {
		em.createNativeQuery("update purchase_offer set created_at = ? where id = ?")
				.setParameter(1, createdAt)
				.setParameter(2, offer.getId())
				.executeUpdate();
		em.clear();
	}
}
