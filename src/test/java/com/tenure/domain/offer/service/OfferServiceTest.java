package com.tenure.domain.offer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.domain.notification.enums.NotificationSettingType;
import com.tenure.domain.notification.enums.NotificationTargetType;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.offer.enums.OfferCancelReason;
import com.tenure.domain.offer.enums.OfferStatus;
import com.tenure.domain.offer.repository.PurchaseOfferRepository;
import com.tenure.domain.payment.service.PaymentService;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import java.lang.reflect.Constructor;
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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 탈퇴에 따른 제안 정리만 다룬다. 나머지 OfferService 메서드(생성·수락·거절·취소 등)는 아직
 * 단위 테스트가 없고 OfferConcurrencyTest(통합)로만 덮여 있다 — 이번 작업 범위 밖이라 손대지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OfferService")
class OfferServiceTest {

	private static final Long BUYER_ID = 1L;
	private static final Long SELLER_ID = 2L;
	private static final Long SENT_OFFER_ID = 10L;
	private static final Long RECEIVED_OFFER_ID = 20L;

	@Mock private PurchaseOfferRepository purchaseOfferRepository;
	@Mock private PaymentService paymentService;
	@Mock private NotificationService notificationService;

	@InjectMocks private OfferService offerService;

	@Nested
	@DisplayName("탈퇴에 따른 제안 정리")
	class CancelAndRejectPendingForWithdrawal {

		@Test
		@DisplayName("내가 보낸 PENDING 은 취소하고 결제를 해제한 뒤 판매자에게 알림을 보낸다")
		void withdrawal_sentPending_cancelsReleasesAndNotifiesSeller() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			PurchaseOffer offer = pendingOffer(SENT_OFFER_ID, buyer, seller);

			given(purchaseOfferRepository.findPendingIdsByBuyerOrSellerId(BUYER_ID)).willReturn(List.of(SENT_OFFER_ID));
			given(purchaseOfferRepository.findByIdForUpdate(SENT_OFFER_ID)).willReturn(Optional.of(offer));

			boolean concurrentTradeFound = offerService.cancelAndRejectPendingForWithdrawal(BUYER_ID);

			assertThat(concurrentTradeFound).isFalse();
			assertThat(offer.getStatus()).isEqualTo(OfferStatus.CANCELED);
			assertThat(offer.getCancelReason()).isEqualTo(OfferCancelReason.WITHDRAWN);

			verify(paymentService).release(eq(SENT_OFFER_ID), any());
			verify(notificationService).notify(
					eq(seller), eq(NotificationSettingType.REQUEST_AUTO_CANCEL), eq(NotificationCategory.TRADE_STATUS),
					any(), any(), any(), eq(NotificationTargetType.OFFER), eq(SENT_OFFER_ID));
		}

		@Test
		@DisplayName("내가 받은 PENDING 도 취소 처리하고(거절 아님) 결제를 해제한 뒤 구매자에게 알림을 보낸다")
		void withdrawal_receivedPending_cancelsReleasesAndNotifiesBuyer() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			PurchaseOffer offer = pendingOffer(RECEIVED_OFFER_ID, buyer, seller);

			given(purchaseOfferRepository.findPendingIdsByBuyerOrSellerId(SELLER_ID)).willReturn(List.of(RECEIVED_OFFER_ID));
			given(purchaseOfferRepository.findByIdForUpdate(RECEIVED_OFFER_ID)).willReturn(Optional.of(offer));

			boolean concurrentTradeFound = offerService.cancelAndRejectPendingForWithdrawal(SELLER_ID);

			assertThat(concurrentTradeFound).isFalse();
			// reject() 가 아니라 cancel(WITHDRAWN) 이어야 함 — uk_purchase_offer_limit 이
			// REJECTED 는 면제해주지 않아 reject() 로 끝내면 구매자의 1회 제한이 영구 소진된다
			assertThat(offer.getStatus()).isEqualTo(OfferStatus.CANCELED);
			assertThat(offer.getCancelReason()).isEqualTo(OfferCancelReason.WITHDRAWN);

			verify(paymentService).release(eq(RECEIVED_OFFER_ID), any());
			verify(notificationService).notify(
					eq(buyer), eq(NotificationSettingType.REQUEST_AUTO_CANCEL), eq(NotificationCategory.TRADE_STATUS),
					any(), any(), any(), eq(NotificationTargetType.OFFER), eq(RECEIVED_OFFER_ID));
		}

		@Test
		@DisplayName("재잠금 사이 거절·취소·만료 등으로 이미 끝난 건은 건드리지 않고 거래 발견으로도 안 본다")
		void withdrawal_alreadyFinishedBetweenFetchAndLock_skipsSafely() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			PurchaseOffer alreadyExpired = pendingOffer(SENT_OFFER_ID, buyer, seller);
			alreadyExpired.expire(LocalDateTime.now());

			given(purchaseOfferRepository.findPendingIdsByBuyerOrSellerId(BUYER_ID)).willReturn(List.of(SENT_OFFER_ID));
			given(purchaseOfferRepository.findByIdForUpdate(SENT_OFFER_ID)).willReturn(Optional.of(alreadyExpired));

			boolean concurrentTradeFound = offerService.cancelAndRejectPendingForWithdrawal(BUYER_ID);

			assertThat(concurrentTradeFound).isFalse();
			verify(paymentService, never()).release(any(), any());
			verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("재잠금 사이 상대가 먼저 수락해 거래가 생긴 건은 손대지 않고 거래 발견으로 알려준다")
		void withdrawal_alreadyAcceptedBetweenFetchAndLock_reportsConcurrentTrade() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			PurchaseOffer alreadyAccepted = pendingOffer(SENT_OFFER_ID, buyer, seller);
			alreadyAccepted.accept(LocalDateTime.now());

			given(purchaseOfferRepository.findPendingIdsByBuyerOrSellerId(BUYER_ID)).willReturn(List.of(SENT_OFFER_ID));
			given(purchaseOfferRepository.findByIdForUpdate(SENT_OFFER_ID)).willReturn(Optional.of(alreadyAccepted));

			boolean concurrentTradeFound = offerService.cancelAndRejectPendingForWithdrawal(BUYER_ID);

			assertThat(concurrentTradeFound).isTrue();
			assertThat(alreadyAccepted.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
			verify(paymentService, never()).release(any(), any());
			verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("보낸 제안과 받은 제안이 여러 건씩 섞여 있어도 전부 취소 처리한다")
		void withdrawal_multipleSentAndReceivedPending_processesAllOfThem() {
			Long sentOfferId2 = 11L;
			Long receivedOfferId2 = 21L;

			User withdrawing = user(BUYER_ID);
			User otherSeller1 = user(SELLER_ID);
			User otherSeller2 = user(3L);
			User otherBuyer1 = user(4L);
			User otherBuyer2 = user(5L);

			PurchaseOffer sent1 = pendingOffer(SENT_OFFER_ID, withdrawing, otherSeller1);
			PurchaseOffer sent2 = pendingOffer(sentOfferId2, withdrawing, otherSeller2);
			PurchaseOffer received1 = pendingOffer(RECEIVED_OFFER_ID, otherBuyer1, withdrawing);
			PurchaseOffer received2 = pendingOffer(receivedOfferId2, otherBuyer2, withdrawing);

			// 하나로 합친 쿼리는 id 오름차순이라 보낸 것·받은 것이 섞여 나올 수 있음을 그대로 반영
			given(purchaseOfferRepository.findPendingIdsByBuyerOrSellerId(BUYER_ID))
					.willReturn(List.of(SENT_OFFER_ID, sentOfferId2, RECEIVED_OFFER_ID, receivedOfferId2));
			given(purchaseOfferRepository.findByIdForUpdate(SENT_OFFER_ID)).willReturn(Optional.of(sent1));
			given(purchaseOfferRepository.findByIdForUpdate(sentOfferId2)).willReturn(Optional.of(sent2));
			given(purchaseOfferRepository.findByIdForUpdate(RECEIVED_OFFER_ID)).willReturn(Optional.of(received1));
			given(purchaseOfferRepository.findByIdForUpdate(receivedOfferId2)).willReturn(Optional.of(received2));

			boolean concurrentTradeFound = offerService.cancelAndRejectPendingForWithdrawal(BUYER_ID);

			assertThat(concurrentTradeFound).isFalse();
			assertThat(sent1.getStatus()).isEqualTo(OfferStatus.CANCELED);
			assertThat(sent2.getStatus()).isEqualTo(OfferStatus.CANCELED);
			assertThat(sent1.getCancelReason()).isEqualTo(OfferCancelReason.WITHDRAWN);
			assertThat(sent2.getCancelReason()).isEqualTo(OfferCancelReason.WITHDRAWN);
			assertThat(received1.getStatus()).isEqualTo(OfferStatus.CANCELED);
			assertThat(received2.getStatus()).isEqualTo(OfferStatus.CANCELED);
			assertThat(received1.getCancelReason()).isEqualTo(OfferCancelReason.WITHDRAWN);
			assertThat(received2.getCancelReason()).isEqualTo(OfferCancelReason.WITHDRAWN);

			verify(paymentService, times(4)).release(any(), any());
			verify(notificationService, times(4))
					.notify(any(), any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("보낸 제안 중 하나가 먼저 수락돼도 나머지 PENDING 건은 계속 처리한다")
		void withdrawal_oneOfMultipleSentAlreadyAccepted_stillProcessesTheRest() {
			Long acceptedOfferId = SENT_OFFER_ID;
			Long stillPendingOfferId = 11L;

			User withdrawing = user(BUYER_ID);
			User seller1 = user(SELLER_ID);
			User seller2 = user(3L);

			PurchaseOffer accepted = pendingOffer(acceptedOfferId, withdrawing, seller1);
			accepted.accept(LocalDateTime.now());
			PurchaseOffer stillPending = pendingOffer(stillPendingOfferId, withdrawing, seller2);

			given(purchaseOfferRepository.findPendingIdsByBuyerOrSellerId(BUYER_ID))
					.willReturn(List.of(acceptedOfferId, stillPendingOfferId));
			given(purchaseOfferRepository.findByIdForUpdate(acceptedOfferId)).willReturn(Optional.of(accepted));
			given(purchaseOfferRepository.findByIdForUpdate(stillPendingOfferId)).willReturn(Optional.of(stillPending));

			boolean concurrentTradeFound = offerService.cancelAndRejectPendingForWithdrawal(BUYER_ID);

			// 먼저 수락된 건을 만나 true 로 바뀐 신호가 뒤이은 정상 처리로 다시 false 가 되지 않는다
			assertThat(concurrentTradeFound).isTrue();
			assertThat(accepted.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
			// 수락된 건과 별개로 나머지는 그대로 취소 처리된다 — 하나의 충돌이 전체 정리를 멈추지 않는다
			assertThat(stillPending.getStatus()).isEqualTo(OfferStatus.CANCELED);
			assertThat(stillPending.getCancelReason()).isEqualTo(OfferCancelReason.WITHDRAWN);

			verify(paymentService, times(1)).release(eq(stillPendingOfferId), any());
			verify(notificationService, times(1))
					.notify(any(), any(), any(), any(), any(), any(), any(), any());
		}
	}

	/** PENDING 제안 최소 픽스처. 이 테스트가 보는 필드(item·buyer·seller·status)만 채운다 —
	 * 나머지는 OfferService 의 이 경로가 읽지 않는다 */
	private static PurchaseOffer pendingOffer(Long offerId, User buyer, User seller) {
		PurchaseOffer offer = newInstance(PurchaseOffer.class);
		ReflectionTestUtils.setField(offer, "id", offerId);
		ReflectionTestUtils.setField(offer, "item", item());
		ReflectionTestUtils.setField(offer, "buyer", buyer);
		ReflectionTestUtils.setField(offer, "seller", seller);
		ReflectionTestUtils.setField(offer, "status", OfferStatus.PENDING);
		return offer;
	}

	private static Item item() {
		Item item = newInstance(Item.class);
		ReflectionTestUtils.setField(item, "id", 100L);
		ReflectionTestUtils.setField(item, "name", "긴소매 티셔츠");
		ReflectionTestUtils.setField(item, "coverImageKey", "item/2026/09/cover.jpg");
		return item;
	}

	private static User user(Long id) {
		User user = User.signUp(
				"u%d@tenure.local".formatted(id), "hashed", "홍길동",
				"821012345678", "nick%d".formatted(id), null, Gender.MALE, 170, 64
		);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	/** protected 기본 생성자(JPA 전용)를 테스트에서만 열어 빈 엔티티를 만든다 */
	private static <T> T newInstance(Class<T> type) {
		try {
			Constructor<T> constructor = type.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("테스트 픽스처 생성 실패: " + type.getSimpleName(), e);
		}
	}
}
