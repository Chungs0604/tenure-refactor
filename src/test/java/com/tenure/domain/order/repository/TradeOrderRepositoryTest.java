package com.tenure.domain.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.category.entity.Category;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.order.entity.OrderShipment;
import com.tenure.domain.order.entity.OrderStatusHistory;
import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.settlement.entity.Settlement;
import com.tenure.domain.settlement.repository.SettlementRepository;
import com.tenure.domain.payout.enums.BankCode;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.global.pagination.Cursor;
import com.tenure.support.RepositorySliceTestSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 거래 조회 쿼리.
 *
 * H2 가 아니라 실제 MySQL 에서 돈다. 커서 페이지네이션은 size + 1 건을 읽어 다음 페이지
 * 유무를 판정하는 방식이라 그 경계를 여기서 확인해 둔다.
 */
@DisplayName("거래 조회 쿼리")
class TradeOrderRepositoryTest extends RepositorySliceTestSupport {

	/** V2 시드의 고정 id — 상의 · 긴소매 티셔츠 */
	private static final Long TOP_SHIRT = 116L;

	@Autowired private EntityManager em;
	@Autowired private TradeOrderRepository tradeOrderRepository;
	@Autowired private OrderShipmentRepository orderShipmentRepository;
	@Autowired private OrderStatusHistoryRepository orderStatusHistoryRepository;
	@Autowired private SettlementRepository settlementRepository;

	private User buyer;
	private User seller;

	@BeforeEach
	void setUp() {
		buyer = persistUser("buyer@tenure.app", "Buyer");
		seller = persistUser("seller@tenure.app", "Seller");
	}

	@Nested
	@DisplayName("상세·잠금 조회")
	class DetailAndLock {

		@Test
		@DisplayName("상세 조회는 연관 엔티티를 함께 읽어 추가 조회 없이 접근할 수 있다")
		void findDetailById_fetchesAssociationsEagerly() {
			TradeOrder order = persistOrder(persistItem(), OrderStatus.PAYMENT_COMPLETED);
			em.clear();

			TradeOrder found = tradeOrderRepository.findDetailById(order.getId()).orElseThrow();

			// em.clear() 뒤라 지연 로딩이면 LazyInitializationException 이 나야 한다
			assertThat(found.getItem().getName()).isNotNull();
			assertThat(found.getBuyer().getNickname()).isEqualTo("Buyer");
			assertThat(found.getSeller().getNickname()).isEqualTo("Seller");
			assertThat(found.getOffer().getId()).isNotNull();
		}

		@Test
		@DisplayName("없는 거래면 비어 있다")
		void findDetailById_unknownOrder_empty() {
			assertThat(tradeOrderRepository.findDetailById(999_999L)).isEmpty();
		}

		@Test
		@DisplayName("잠금 조회는 id 로 찾은 행을 그대로 돌려준다")
		void findByIdForUpdate_returnsOrder() {
			TradeOrder order = persistOrder(persistItem(), OrderStatus.PAYMENT_COMPLETED);

			assertThat(tradeOrderRepository.findByIdForUpdate(order.getId()))
					.get().extracting(TradeOrder::getId).isEqualTo(order.getId());
		}
	}

	@Nested
	@DisplayName("진행 중 거래 존재 여부")
	class ExistsInProgress {

		@Test
		@DisplayName("종료 상태(정산완료·취소)가 아니면 진행 중이다")
		void existsInProgress_notFinished_true() {
			Item item = persistItem();
			persistOrder(item, OrderStatus.SHIPPED);

			assertThat(tradeOrderRepository.existsInProgress(item.getId(), finishedStatuses())).isTrue();
		}

		@Test
		@DisplayName("정산완료·취소뿐이면 진행 중이 아니다")
		void existsInProgress_allFinished_false() {
			Item item = persistItem();
			persistOrder(item, OrderStatus.SETTLED);

			assertThat(tradeOrderRepository.existsInProgress(item.getId(), finishedStatuses())).isFalse();
		}

		@Test
		@DisplayName("거래가 아예 없으면 진행 중이 아니다")
		void existsInProgress_noOrder_false() {
			Item item = persistItem();

			assertThat(tradeOrderRepository.existsInProgress(item.getId(), finishedStatuses())).isFalse();
		}

		private Set<OrderStatus> finishedStatuses() {
			return EnumSet.of(OrderStatus.SETTLED, OrderStatus.CANCELED);
		}
	}

	@Nested
	@DisplayName("자동 구매 확정 대상")
	class AutoConfirmable {

		@Test
		@DisplayName("배송완료 +72h 지난 건만 담는다")
		void findAutoConfirmableIds_onlyPastDue() {
			Item item = persistItem();
			TradeOrder due = persistOrder(item, OrderStatus.DELIVERED);
			backdateAutoConfirmAt(due, LocalDateTime.now().minusHours(1));

			Item item2 = persistItem();
			TradeOrder notYet = persistOrder(item2, OrderStatus.DELIVERED);
			backdateAutoConfirmAt(notYet, LocalDateTime.now().plusHours(1));

			List<Long> ids = tradeOrderRepository.findAutoConfirmableIds(LocalDateTime.now());

			assertThat(ids).contains(due.getId()).doesNotContain(notYet.getId());
		}

		@Test
		@DisplayName("DELIVERED 가 아니면 담지 않는다")
		void findAutoConfirmableIds_excludesOtherStatuses() {
			Item item = persistItem();
			TradeOrder shipped = persistOrder(item, OrderStatus.SHIPPED);

			assertThat(tradeOrderRepository.findAutoConfirmableIds(LocalDateTime.now().plusDays(10)))
					.doesNotContain(shipped.getId());
		}
	}

	@Nested
	@DisplayName("판매·구매 내역 커서 페이지네이션")
	class FeedCursor {

		@Test
		@DisplayName("판매 내역은 최신순이고 상태를 지정하면 그 상태만 담는다")
		void findAllBySellerForFeed_filtersByStatusAndOrdersDesc() {
			TradeOrder shipped = persistOrder(persistItem(), OrderStatus.SHIPPED);
			TradeOrder settled = persistOrder(persistItem(), OrderStatus.SETTLED);

			List<TradeOrder> inProgress = tradeOrderRepository.findAllBySellerForFeed(
					seller.getId(), Set.of(OrderStatus.SHIPPED), null, 20);

			assertThat(inProgress).extracting(TradeOrder::getId).containsExactly(shipped.getId());
			assertThat(inProgress).extracting(TradeOrder::getId).doesNotContain(settled.getId());
		}

		@Test
		@DisplayName("상태가 null 이면 전체 탭으로 모든 상태를 담는다")
		void findAllBySellerForFeed_nullStatuses_includesAll() {
			TradeOrder shipped = persistOrder(persistItem(), OrderStatus.SHIPPED);
			TradeOrder settled = persistOrder(persistItem(), OrderStatus.SETTLED);

			List<TradeOrder> all = tradeOrderRepository.findAllBySellerForFeed(seller.getId(), null, null, 20);

			assertThat(all).extracting(TradeOrder::getId)
					.contains(shipped.getId(), settled.getId());
		}

		@Test
		@DisplayName("size + 1 건까지 읽는다 — 다음 페이지 유무를 세지 않고 안다")
		void findAllBySellerForFeed_readsOneMoreThanSize() {
			persistOrder(persistItem(), OrderStatus.SHIPPED);
			persistOrder(persistItem(), OrderStatus.SHIPPED);
			persistOrder(persistItem(), OrderStatus.SHIPPED);

			assertThat(tradeOrderRepository.findAllBySellerForFeed(seller.getId(), null, null, 2)).hasSize(3);
		}

		@Test
		@DisplayName("커서를 주면 그 뒤부터 가져온다")
		void findAllBySellerForFeed_withCursor_returnsRowsAfterIt() {
			TradeOrder first = persistOrder(persistItem(), OrderStatus.SHIPPED);
			TradeOrder second = persistOrder(persistItem(), OrderStatus.SHIPPED);
			em.clear();

			TradeOrder lastOfFirstPage =
					tradeOrderRepository.findAllBySellerForFeed(seller.getId(), null, null, 1).get(0);
			Cursor cursor = Cursor.of(lastOfFirstPage.getCreatedAt(), lastOfFirstPage.getId());

			List<TradeOrder> next = tradeOrderRepository.findAllBySellerForFeed(seller.getId(), null, cursor, 20);

			assertThat(lastOfFirstPage.getId()).isEqualTo(second.getId());
			assertThat(next).extracting(TradeOrder::getId).containsExactly(first.getId());
		}

		@Test
		@DisplayName("생성일이 같아도 id 로 갈라 건너뛰지 않는다")
		void findAllBySellerForFeed_sameCreatedAt_tieBreaksById() {
			TradeOrder first = persistOrder(persistItem(), OrderStatus.SHIPPED);
			TradeOrder second = persistOrder(persistItem(), OrderStatus.SHIPPED);
			LocalDateTime sameMoment = LocalDateTime.now().withNano(0);
			backdateCreatedAt(first, sameMoment);
			backdateCreatedAt(second, sameMoment);

			List<TradeOrder> firstPage = tradeOrderRepository.findAllBySellerForFeed(seller.getId(), null, null, 1);
			TradeOrder head = firstPage.get(0);
			List<TradeOrder> next = tradeOrderRepository.findAllBySellerForFeed(
					seller.getId(), null, Cursor.of(head.getCreatedAt(), head.getId()), 20);

			assertThat(head.getId()).isEqualTo(second.getId());
			assertThat(next).extracting(TradeOrder::getId).containsExactly(first.getId());
		}

		@Test
		@DisplayName("구매 내역도 최신순이고 상태를 지정하면 그 상태만 담는다")
		void findAllByBuyerForFeed_filtersByStatusAndOrdersDesc() {
			TradeOrder shipped = persistOrder(persistItem(), OrderStatus.SHIPPED);
			TradeOrder settled = persistOrder(persistItem(), OrderStatus.SETTLED);

			List<TradeOrder> done = tradeOrderRepository.findAllByBuyerForFeed(
					buyer.getId(), Set.of(OrderStatus.SETTLED), null, 20);

			assertThat(done).extracting(TradeOrder::getId).containsExactly(settled.getId());
			assertThat(done).extracting(TradeOrder::getId).doesNotContain(shipped.getId());
		}

		@Test
		@DisplayName("남의 거래는 담기지 않는다")
		void findAllByBuyerForFeed_othersOrder_excluded() {
			User otherBuyer = persistUser("other@tenure.app", "Other");
			PurchaseOffer offerOfOther = persistOffer(persistItem(), otherBuyer, seller);
			persistOrder(offerOfOther, OrderStatus.SHIPPED);

			assertThat(tradeOrderRepository.findAllByBuyerForFeed(buyer.getId(), null, null, 20)).isEmpty();
		}
	}

	@Nested
	@DisplayName("배송·이력·정산")
	class ShipmentHistorySettlement {

		@Test
		@DisplayName("거래당 배송 행은 하나다")
		void orderShipment_findByOrderId_returnsOne() {
			TradeOrder order = persistOrder(persistItem(), OrderStatus.PAYMENT_COMPLETED);
			OrderShipment shipment = OrderShipment.fromOffer(order, order.getOffer());
			em.persist(shipment);
			flush();

			assertThat(orderShipmentRepository.findByOrderId(order.getId()))
					.get().extracting(OrderShipment::getId).isEqualTo(shipment.getId());
		}

		@Test
		@DisplayName("타임라인은 단계 도달 순서대로 나온다")
		void orderStatusHistory_findByOrderId_ordersByChangedAtAsc() {
			TradeOrder order = persistOrder(persistItem(), OrderStatus.SHIPPED);
			LocalDateTime t0 = LocalDateTime.now().minusDays(2);
			LocalDateTime t1 = LocalDateTime.now().minusDays(1);
			em.persist(OrderStatusHistory.of(order, OrderStatus.SHIPPED, seller, t1));
			em.persist(OrderStatusHistory.of(order, OrderStatus.PAYMENT_COMPLETED, buyer, t0));
			flush();

			List<OrderStatusHistory> histories = orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(order.getId());

			assertThat(histories).extracting(OrderStatusHistory::getStatus)
					.containsExactly(OrderStatus.PAYMENT_COMPLETED, OrderStatus.SHIPPED);
		}

		@Test
		@DisplayName("정산 배치 대상은 settled_at 이 비어 있는 건만 담고, 거래도 함께 읽는다")
		void settlement_findAllBySettledAtIsNull_fetchesOrder() {
			TradeOrder order = persistOrder(persistItem(), OrderStatus.PURCHASE_CONFIRMED);
			Settlement pending = Settlement.of(order, seller, BankCode.TOSS_BANK.name(), "1111-1111", "홍길동", 365000);
			em.persist(pending);

			TradeOrder settledOrder = persistOrder(persistItem(), OrderStatus.SETTLED);
			Settlement done = Settlement.of(settledOrder, seller, BankCode.TOSS_BANK.name(), "2222-2222", "홍길동", 365000);
			done.settle(LocalDateTime.now());
			em.persist(done);
			flush();
			em.clear();

			List<Settlement> dueSettlements = settlementRepository.findAllBySettledAtIsNull();

			assertThat(dueSettlements).extracting(Settlement::getId).containsExactly(pending.getId());
			// fetch join 이라 프록시 초기화 없이 읽힌다
			assertThat(dueSettlements.get(0).getOrder().getStatus()).isEqualTo(OrderStatus.PURCHASE_CONFIRMED);
		}

		@Test
		@DisplayName("거래당 정산 행은 하나다")
		void settlement_findByOrderId_returnsOne() {
			TradeOrder order = persistOrder(persistItem(), OrderStatus.PURCHASE_CONFIRMED);
			Settlement settlement = Settlement.of(order, seller, BankCode.TOSS_BANK.name(), "1111-1111", "홍길동", 365000);
			em.persist(settlement);
			flush();

			assertThat(settlementRepository.findByOrderId(order.getId()))
					.get().extracting(Settlement::getId).isEqualTo(settlement.getId());
		}
	}

	// ── 픽스처

	private User persistUser(String email, String nickname) {
		User user = User.signUp(email, "$2a$10$hashed", "홍길동",
				"821012345678", nickname, null, Gender.MALE, 170, 64);
		em.persist(user);
		flush();
		return user;
	}

	private Item persistItem() {
		Category category = em.find(Category.class, TOP_SHIRT);
		Item item = Item.register(seller, category, "LVC 1955 501", "Levis", "L",
				WearTarget.UNISEX, "item/2026/09/a.jpg", LocalDate.of(2025, 2, 8));
		em.persist(item);
		flush();
		return item;
	}

	private PurchaseOffer persistOffer(Item item, User offerBuyer, User offerSeller) {
		// 기본 배송지는 사용자당 1개만 허용된다(uk_shipping_address_default) — 같은 구매자로
		// 여러 거래를 만들 때마다 기본으로 등록하면 두 번째부터 충돌한다
		ShippingAddress address = ShippingAddress.register(offerBuyer, "홍길동", "010-1234-5678", "07027",
				"서울특별시 동작구 상도로 369", "104동 201호", null, null, false);
		em.persist(address);
		PurchaseOffer offer = PurchaseOffer.create(
				null, item, offerBuyer, offerSeller, OfferType.PURCHASE_OFFER,
				360000, 5000, FeeBearer.SELLER, new BigDecimal("0.0300"),
				0, 10800, 375800, 365000, "직거래도 가능할까요?", address, LocalDateTime.now());
		em.persist(offer);
		flush();
		return offer;
	}

	private TradeOrder persistOrder(Item item, OrderStatus status) {
		return persistOrder(persistOffer(item, buyer, seller), status);
	}

	private TradeOrder persistOrder(PurchaseOffer offer, OrderStatus status) {
		LocalDateTime now = LocalDateTime.now();
		TradeOrder order = TradeOrder.createFromOffer(offer, "2026" + System.nanoTime(), now);
		em.persist(order);
		flush();

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
		flush();
		return order;
	}

	/** autoConfirmAt 은 deliver() 가 '배송 완료 시각 + 72h' 로 고정하므로, 경계 테스트는 직접 되돌린다. */
	private void backdateAutoConfirmAt(TradeOrder order, LocalDateTime autoConfirmAt) {
		em.createNativeQuery("update trade_order set auto_confirm_at = ? where id = ?")
				.setParameter(1, autoConfirmAt)
				.setParameter(2, order.getId())
				.executeUpdate();
		em.clear();
	}

	/** 생성일은 감사 컬럼이라 엔티티로는 과거로 돌릴 수 없다. */
	private void backdateCreatedAt(TradeOrder order, LocalDateTime createdAt) {
		em.createNativeQuery("update trade_order set created_at = ? where id = ?")
				.setParameter(1, createdAt)
				.setParameter(2, order.getId())
				.executeUpdate();
		em.clear();
	}

	private void flush() {
		em.flush();
	}
}
