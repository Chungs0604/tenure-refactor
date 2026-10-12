package com.tenure.domain.offer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.address.repository.ShippingAddressRepository;
import com.tenure.domain.block.repository.UserBlockRepository;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.listing.exception.ListingErrorCode;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.domain.offer.dto.request.OfferCreateRequest;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.offer.exception.OfferErrorCode;
import com.tenure.domain.offer.repository.PurchaseOfferRepository;
import com.tenure.domain.payment.client.TossConfirmResponse;
import com.tenure.domain.payment.enums.PaymentMethod;
import com.tenure.domain.payment.service.PaymentService;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 탈퇴에 따른 제안 정리와, 제안 생성(create)의 잠금·예외 번역만 다룬다. 나머지 OfferService
 * 메서드(수락·거절·취소 등)는 아직 단위 테스트가 없고 OfferConcurrencyTest(통합)로만 덮여
 * 있다 — 이번 작업 범위 밖이라 손대지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OfferService")
class OfferServiceTest {

	private static final Long BUYER_ID = 1L;
	private static final Long SELLER_ID = 2L;
	private static final Long SENT_OFFER_ID = 10L;
	private static final Long RECEIVED_OFFER_ID = 20L;
	private static final Long ITEM_ID = 100L;

	@Mock private PurchaseOfferRepository purchaseOfferRepository;
	@Mock private PaymentService paymentService;
	@Mock private OfferBatchExecutor offerBatchExecutor;
	@Mock private NotificationService notificationService;
	@Mock private ItemRepository itemRepository;
	@Mock private ItemListingRepository itemListingRepository;
	@Mock private ItemOwnershipRepository itemOwnershipRepository;
	@Mock private ShippingAddressRepository shippingAddressRepository;
	@Mock private UserBlockRepository userBlockRepository;
	@Mock private UserRepository userRepository;

	@InjectMocks private OfferService offerService;

	/**
	 * 건별 취소·결제 해제·알림은 OfferBatchExecutor.cancelForWithdrawalIfPending 으로 옮겼다 —
	 * OfferBatchExecutorTest 참고. 여기서는 대상 id마다 그 메서드가 독립적으로(건별 REQUIRES_NEW
	 * 트랜잭션으로) 호출되고, 반환값(거래 발견 여부)을 올바르게 합산하는지, 한 건이 예외를
	 * 던져도 나머지는 계속 처리하는지만 본다.
	 */
	@Nested
	@DisplayName("탈퇴에 따른 제안 정리")
	class CancelAndRejectPendingForWithdrawal {

		@Test
		@DisplayName("대상이 없으면 배치 실행기를 호출하지 않는다")
		void withdrawal_noTargets_doesNothing() {
			given(purchaseOfferRepository.findPendingIdsByBuyerOrSellerId(BUYER_ID)).willReturn(List.of());

			boolean concurrentTradeFound = offerService.cancelAndRejectPendingForWithdrawal(BUYER_ID);

			assertThat(concurrentTradeFound).isFalse();
			verify(offerBatchExecutor, never()).cancelForWithdrawalIfPending(any(), any(), any());
		}

		@Test
		@DisplayName("대상 id마다 배치 실행기를 호출하고 거래 발견 여부를 합산한다")
		void withdrawal_targets_delegatesEachIdAndAggregates() {
			given(purchaseOfferRepository.findPendingIdsByBuyerOrSellerId(BUYER_ID))
					.willReturn(List.of(SENT_OFFER_ID, RECEIVED_OFFER_ID));
			given(offerBatchExecutor.cancelForWithdrawalIfPending(eq(SENT_OFFER_ID), eq(BUYER_ID), any()))
					.willReturn(false);
			given(offerBatchExecutor.cancelForWithdrawalIfPending(eq(RECEIVED_OFFER_ID), eq(BUYER_ID), any()))
					.willReturn(true);

			boolean concurrentTradeFound = offerService.cancelAndRejectPendingForWithdrawal(BUYER_ID);

			// 먼저 처리된 건이 신호를 false 로 돌려줘도, 뒤이은 건의 true 가 덮이지 않아야 함
			assertThat(concurrentTradeFound).isTrue();
			verify(offerBatchExecutor).cancelForWithdrawalIfPending(eq(SENT_OFFER_ID), eq(BUYER_ID), any());
			verify(offerBatchExecutor).cancelForWithdrawalIfPending(eq(RECEIVED_OFFER_ID), eq(BUYER_ID), any());
		}

		@Test
		@DisplayName("한 건이 예외를 던져도 나머지 건은 계속 처리하지만, 실패가 있었으니 탈퇴는 막는다")
		void withdrawal_oneThrows_othersContinueButWithdrawalIsBlocked() {
			given(purchaseOfferRepository.findPendingIdsByBuyerOrSellerId(BUYER_ID))
					.willReturn(List.of(SENT_OFFER_ID, RECEIVED_OFFER_ID));
			doThrow(new RuntimeException("토스 취소 실패"))
					.when(offerBatchExecutor).cancelForWithdrawalIfPending(eq(SENT_OFFER_ID), eq(BUYER_ID), any());
			given(offerBatchExecutor.cancelForWithdrawalIfPending(eq(RECEIVED_OFFER_ID), eq(BUYER_ID), any()))
					.willReturn(false);

			boolean concurrentTradeFound = offerService.cancelAndRejectPendingForWithdrawal(BUYER_ID);

			// 환불 실패가 하나라도 있으면 탈퇴를 막는다 — 안 그러면 익명화된 사용자에게 아직
			// 살아있는 결제가 붙은 제안이 남는다
			assertThat(concurrentTradeFound).isTrue();
			verify(offerBatchExecutor).cancelForWithdrawalIfPending(eq(RECEIVED_OFFER_ID), eq(BUYER_ID), any());
		}

		@Test
		@DisplayName("이미 수락된 건이 있으면 아무것도 취소·환불하지 않고 바로 탈퇴를 막는다")
		void withdrawal_alreadyAccepted_blocksBeforeCancelingAnything() {
			given(purchaseOfferRepository.findPendingIdsByBuyerOrSellerId(BUYER_ID))
					.willReturn(List.of(SENT_OFFER_ID, RECEIVED_OFFER_ID));
			given(offerBatchExecutor.isAcceptedNow(SENT_OFFER_ID)).willReturn(false);
			given(offerBatchExecutor.isAcceptedNow(RECEIVED_OFFER_ID)).willReturn(true);

			boolean concurrentTradeFound = offerService.cancelAndRejectPendingForWithdrawal(BUYER_ID);

			assertThat(concurrentTradeFound).isTrue();
			// 2단계(실제 취소·환불)로는 아예 넘어가지 않는다
			verify(offerBatchExecutor, never()).cancelForWithdrawalIfPending(any(), any(), any());
		}
	}

	@Nested
	@DisplayName("구매 제안 / 거래 의사 전송")
	class Create {

		@Test
		@DisplayName("판매 완료된 상품이면 잠금 조회로 다시 확인하고 LISTING_409_SOLD 를 던진다")
		void create_itemAlreadySold_locksAndThrowsSold() {
			Item soldItem = offerItem(SaleStatus.SOLD, true);
			given(itemRepository.findActiveForUpdate(ITEM_ID)).willReturn(Optional.of(soldItem));

			OfferCreateRequest request = purchaseOfferRequest();

			assertThatThrownBy(() -> offerService.create(BUYER_ID, ITEM_ID, request))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(ListingErrorCode.LISTING_409_SOLD);

			// accept() 와 같은 잠금 조회를 쓰는지 — 잠금 없는 조회(findActive)로 되돌아가면
			// 동시 요청에서 SOLD 판정이 커밋되지 않은 값을 볼 수 있다
			verify(itemRepository).findActiveForUpdate(ITEM_ID);
			verify(itemRepository, never()).findActive(any());
		}

		@Test
		@DisplayName("미판매 구매 제안(PURCHASE_OFFER)의 유니크 제약 위반은 OFFER_409_ALREADY_USED 로 번역한다")
		void create_purchaseOfferUniqueViolation_translatesToAlreadyUsed() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item notForSaleItem = offerItem(SaleStatus.NOT_FOR_SALE, true);

			given(itemRepository.findActiveForUpdate(ITEM_ID)).willReturn(Optional.of(notForSaleItem));
			given(itemListingRepository.findOpenByItemId(ITEM_ID)).willReturn(Optional.empty());
			given(userRepository.findById(BUYER_ID)).willReturn(Optional.of(buyer));
			given(itemOwnershipRepository.findCurrent(ITEM_ID))
					.willReturn(Optional.of(ownershipOf(notForSaleItem, seller)));
			given(userBlockRepository.existsMutualBlock(BUYER_ID, SELLER_ID)).willReturn(false);
			given(purchaseOfferRepository.findLatestByItemIdAndBuyerId(ITEM_ID, BUYER_ID)).willReturn(Optional.empty());
			given(shippingAddressRepository.findByIdAndUserId(1L, BUYER_ID))
					.willReturn(Optional.of(shippingAddress(buyer)));
			given(paymentService.confirmWithPg(any(), any(), any())).willReturn(confirmedResponse());
			given(purchaseOfferRepository.save(any())).willThrow(new DataIntegrityViolationException("dup"));

			OfferCreateRequest request = purchaseOfferRequest();

			// uk_purchase_offer_limit(V20) 은 PURCHASE_OFFER 행만 걸리므로, 이 유형에서 난
			// 제약 위반은 1회 제한 재시도로 번역해도 된다
			assertThatThrownBy(() -> offerService.create(BUYER_ID, ITEM_ID, request))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(OfferErrorCode.OFFER_409_ALREADY_USED);

			// 이미 승인된 결제를 되돌리지 않으면 제안도 없이 돈만 빠져나간 상태로 남는다
			verify(paymentService).compensateFailedAuthorization("test-payment-key");
		}

		@Test
		@DisplayName("거래 의사(TRADE_INTENT)의 제약 위반은 1회 제한으로 오역하지 않고 원래 예외를 그대로 전파한다")
		void create_tradeIntentConstraintViolation_propagatesOriginalException() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item onSaleItem = offerItem(SaleStatus.ON_SALE, true);
			ItemListing listing = ItemListing.open(
					onSaleItem, seller, 100_000, 0, FeeBearer.SELLER, null, LocalDateTime.now());

			given(itemRepository.findActiveForUpdate(ITEM_ID)).willReturn(Optional.of(onSaleItem));
			given(itemListingRepository.findOpenByItemId(ITEM_ID)).willReturn(Optional.of(listing));
			given(userRepository.findById(BUYER_ID)).willReturn(Optional.of(buyer));
			given(userBlockRepository.existsMutualBlock(BUYER_ID, SELLER_ID)).willReturn(false);
			given(shippingAddressRepository.findByIdAndUserId(1L, BUYER_ID))
					.willReturn(Optional.of(shippingAddress(buyer)));
			given(paymentService.confirmWithPg(any(), any(), any())).willReturn(confirmedResponse());
			DataIntegrityViolationException original = new DataIntegrityViolationException("other constraint");
			given(purchaseOfferRepository.save(any())).willThrow(original);

			OfferCreateRequest request = OfferCreateRequest.builder()
					.offerType(OfferType.TRADE_INTENT)
					.shippingAddressId(1L)
					.paymentMethod(PaymentMethod.TOSS_PAY)
					.paymentKey("test-payment-key")
					.orderId("test-order-id")
					.build();

			// TRADE_INTENT 는 uk_purchase_offer_limit 대상이 아니므로(V20 함수형 인덱스),
			// 여기서 난 제약 위반을 1회 제한으로 오역하면 안 되고 원래 예외가 그대로 올라와야 한다
			assertThatThrownBy(() -> offerService.create(BUYER_ID, ITEM_ID, request))
					.isSameAs(original);
			// 1회 제한 조회는 PURCHASE_OFFER 에만 거는 로직이라 TRADE_INTENT 에서는 아예 불리지 않음
			verify(purchaseOfferRepository, never()).findLatestByItemIdAndBuyerId(any(), any());
			// 이 분기도 예외 종류와 무관하게 이미 승인된 결제를 되돌려야 한다
			verify(paymentService).compensateFailedAuthorization("test-payment-key");
		}
	}

	private static Item offerItem(SaleStatus saleStatus, boolean offerAllowed) {
		Item item = Item.register(user(SELLER_ID), null, "긴소매 티셔츠", "브랜드", "L",
				WearTarget.UNISEX, "item/2026/09/cover.jpg", LocalDate.of(2025, 2, 8));
		ReflectionTestUtils.setField(item, "id", ITEM_ID);
		item.changeSaleStatus(saleStatus);
		item.changeOfferAllowed(offerAllowed);
		return item;
	}

	private static ItemOwnership ownershipOf(Item item, User owner) {
		return ItemOwnership.openInitial(item, owner, LocalDate.of(2025, 2, 8));
	}

	private static ShippingAddress shippingAddress(User owner) {
		ShippingAddress address = ShippingAddress.register(
				owner, "홍길동", "010-1234-5678", "07027",
				"서울특별시 동작구 상도로 369", "104동 201호", null, null, true);
		ReflectionTestUtils.setField(address, "id", 1L);
		return address;
	}

	private static OfferCreateRequest purchaseOfferRequest() {
		return OfferCreateRequest.builder()
				.offerType(OfferType.PURCHASE_OFFER)
				.offerPrice(100_000)
				.shippingAddressId(1L)
				.paymentMethod(PaymentMethod.TOSS_PAY)
				.paymentKey("test-payment-key")
				.orderId("test-order-id")
				.build();
	}

	private static TossConfirmResponse confirmedResponse() {
		return new TossConfirmResponse("test-payment-key", "test-order-id", 100_000, null);
	}

	private static User user(Long id) {
		User user = User.signUp(
				"u%d@tenure.local".formatted(id), "hashed", "홍길동",
				"821012345678", "nick%d".formatted(id), null, Gender.MALE, 170, 64
		);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}
}
