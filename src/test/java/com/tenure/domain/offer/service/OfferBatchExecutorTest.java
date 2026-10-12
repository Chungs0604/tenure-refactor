package com.tenure.domain.offer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
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
import com.tenure.domain.payment.exception.PaymentErrorCode;
import com.tenure.domain.payment.service.PaymentService;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.global.exception.CustomException;
import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
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
 * 제안 자동 정리 배치의 건별 처리(상태 전이·결제 해제·알림)를 다룬다. REQUIRES_NEW 트랜잭션
 * 분리 자체는 단위 테스트로 검증할 수 없어, 그 효과(한 건 실패가 나머지에 영향 없음)는
 * 통합 테스트(OfferConcurrencyTest)에 맡긴다. 호출부(OfferService)는 대상 id마다 이 메서드들을
 * 위임만 하는지를 OfferServiceTest 에서 따로 본다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OfferBatchExecutor")
class OfferBatchExecutorTest {

	private static final Long BUYER_ID = 1L;
	private static final Long SELLER_ID = 2L;
	private static final Long OFFER_ID = 10L;

	@Mock private PurchaseOfferRepository purchaseOfferRepository;
	@Mock private PaymentService paymentService;
	@Mock private NotificationService notificationService;

	@InjectMocks private OfferBatchExecutor offerBatchExecutor;

	@Nested
	@DisplayName("탈퇴에 따른 건별 정리")
	class CancelForWithdrawalIfPending {

		@Test
		@DisplayName("내가 보낸 PENDING 은 취소하고 결제를 해제한 뒤 판매자에게 알림을 보낸다")
		void sentPending_cancelsReleasesAndNotifiesSeller() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			PurchaseOffer offer = pendingOffer(buyer, seller);
			given(purchaseOfferRepository.findByIdForUpdate(OFFER_ID)).willReturn(Optional.of(offer));

			boolean concurrentTradeFound =
					offerBatchExecutor.cancelForWithdrawalIfPending(OFFER_ID, BUYER_ID, LocalDateTime.now());

			assertThat(concurrentTradeFound).isFalse();
			assertThat(offer.getStatus()).isEqualTo(OfferStatus.CANCELED);
			assertThat(offer.getCancelReason()).isEqualTo(OfferCancelReason.WITHDRAWN);
			verify(paymentService).release(eq(OFFER_ID), any());
			verify(notificationService).notify(
					eq(seller), eq(NotificationSettingType.REQUEST_AUTO_CANCEL), eq(NotificationCategory.TRADE_STATUS),
					any(), any(), any(), eq(NotificationTargetType.OFFER), eq(OFFER_ID));
		}

		@Test
		@DisplayName("내가 받은 PENDING 도 취소 처리하고(거절 아님) 결제를 해제한 뒤 구매자에게 알림을 보낸다")
		void receivedPending_cancelsReleasesAndNotifiesBuyer() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			PurchaseOffer offer = pendingOffer(buyer, seller);
			given(purchaseOfferRepository.findByIdForUpdate(OFFER_ID)).willReturn(Optional.of(offer));

			boolean concurrentTradeFound =
					offerBatchExecutor.cancelForWithdrawalIfPending(OFFER_ID, SELLER_ID, LocalDateTime.now());

			assertThat(concurrentTradeFound).isFalse();
			assertThat(offer.getStatus()).isEqualTo(OfferStatus.CANCELED);
			assertThat(offer.getCancelReason()).isEqualTo(OfferCancelReason.WITHDRAWN);
			verify(paymentService).release(eq(OFFER_ID), any());
			verify(notificationService).notify(
					eq(buyer), eq(NotificationSettingType.REQUEST_AUTO_CANCEL), eq(NotificationCategory.TRADE_STATUS),
					any(), any(), any(), eq(NotificationTargetType.OFFER), eq(OFFER_ID));
		}

		@Test
		@DisplayName("재잠금 사이 거절·취소·만료 등으로 이미 끝난 건은 건드리지 않고 거래 발견으로도 안 본다")
		void alreadyFinished_skipsSafely() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			PurchaseOffer offer = pendingOffer(buyer, seller);
			offer.expire(LocalDateTime.now());
			given(purchaseOfferRepository.findByIdForUpdate(OFFER_ID)).willReturn(Optional.of(offer));

			boolean concurrentTradeFound =
					offerBatchExecutor.cancelForWithdrawalIfPending(OFFER_ID, BUYER_ID, LocalDateTime.now());

			assertThat(concurrentTradeFound).isFalse();
			verify(paymentService, never()).release(any(), any());
			verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("재잠금 사이 상대가 먼저 수락해 거래가 생긴 건은 손대지 않고 거래 발견으로 알려준다")
		void alreadyAccepted_reportsConcurrentTrade() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			PurchaseOffer offer = pendingOffer(buyer, seller);
			offer.accept(LocalDateTime.now());
			given(purchaseOfferRepository.findByIdForUpdate(OFFER_ID)).willReturn(Optional.of(offer));

			boolean concurrentTradeFound =
					offerBatchExecutor.cancelForWithdrawalIfPending(OFFER_ID, BUYER_ID, LocalDateTime.now());

			assertThat(concurrentTradeFound).isTrue();
			assertThat(offer.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
			verify(paymentService, never()).release(any(), any());
			verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any(), any(), any());
		}
	}

	@Nested
	@DisplayName("판매 내림/경쟁 제안 취소 건별 처리")
	class CancelIfPending {

		@Test
		@DisplayName("PENDING 이면 취소하고 결제를 해제한 뒤 알림을 보내며 true 를 돌려준다")
		void pending_cancelsReleasesNotifiesAndReturnsTrue() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			PurchaseOffer offer = pendingOffer(buyer, seller);
			given(purchaseOfferRepository.findByIdForUpdate(OFFER_ID)).willReturn(Optional.of(offer));

			boolean canceled = offerBatchExecutor.cancelIfPending(
					OFFER_ID, OfferCancelReason.OTHER_OFFER_ACCEPTED, LocalDateTime.now(),
					NotificationSettingType.REQUEST_AUTO_CANCEL, "다른 제안이 수락되어 보낸 제안이 취소됐어요.");

			assertThat(canceled).isTrue();
			assertThat(offer.getStatus()).isEqualTo(OfferStatus.CANCELED);
			assertThat(offer.getCancelReason()).isEqualTo(OfferCancelReason.OTHER_OFFER_ACCEPTED);
			verify(paymentService).release(eq(OFFER_ID), any());
			verify(notificationService).notify(
					eq(buyer), eq(NotificationSettingType.REQUEST_AUTO_CANCEL), eq(NotificationCategory.TRADE_STATUS),
					any(), any(), any(), eq(NotificationTargetType.OFFER), eq(OFFER_ID));
		}

		@Test
		@DisplayName("재잠금 사이 이미 끝난 건은 손대지 않고 false 를 돌려준다")
		void alreadyFinished_returnsFalse() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			PurchaseOffer offer = pendingOffer(buyer, seller);
			offer.reject(LocalDateTime.now());
			given(purchaseOfferRepository.findByIdForUpdate(OFFER_ID)).willReturn(Optional.of(offer));

			boolean canceled = offerBatchExecutor.cancelIfPending(
					OFFER_ID, OfferCancelReason.OTHER_OFFER_ACCEPTED, LocalDateTime.now(),
					NotificationSettingType.REQUEST_AUTO_CANCEL, "다른 제안이 수락되어 보낸 제안이 취소됐어요.");

			assertThat(canceled).isFalse();
			verify(paymentService, never()).release(any(), any());
		}

		@Test
		@DisplayName(
				"토스 환불 실패로 결제 해제가 예외를 던지면 그대로 호출부로 전파된다"
						+ " — release() 를 메서드 마지막(알림 뒤)에 두므로, 이미 cancel()·notify() 가"
						+ " 끝난 뒤에 예외가 난다. 그 앞의 변경(엔티티 상태·알림 insert)은 REQUIRES_NEW"
						+ " 트랜잭션 롤백으로 되돌려지는데, 이 단위 테스트는 메모리 상 객체만 보므로"
						+ " 그 롤백 자체는 검증하지 못한다")
		void releaseThrows_propagatesExceptionAfterCancelAndNotifyAlreadyRan() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			PurchaseOffer offer = pendingOffer(buyer, seller);
			given(purchaseOfferRepository.findByIdForUpdate(OFFER_ID)).willReturn(Optional.of(offer));
			willThrow(new CustomException(PaymentErrorCode.PAYMENT_409_RELEASE))
					.given(paymentService).release(eq(OFFER_ID), any());

			assertThatThrownBy(() -> offerBatchExecutor.cancelIfPending(
					OFFER_ID, OfferCancelReason.OTHER_OFFER_ACCEPTED, LocalDateTime.now(),
					NotificationSettingType.REQUEST_AUTO_CANCEL, "다른 제안이 수락되어 보낸 제안이 취소됐어요."))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(PaymentErrorCode.PAYMENT_409_RELEASE);

			// release() 가 마지막이라 그 전의 offer.cancel() · notify() 는 이미 실행됨(메모리상)
			assertThat(offer.getStatus()).isEqualTo(OfferStatus.CANCELED);
			verify(notificationService).notify(any(), any(), any(), any(), any(), any(), any(), any());
		}
	}

	@Nested
	@DisplayName("자동 만료 건별 처리")
	class ExpireIfPending {

		@Test
		@DisplayName("PENDING 이고 만료 시각이 지났으면 만료 처리하고 결제를 해제한다")
		void pendingAndExpired_expiresAndReleases() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			PurchaseOffer offer = pendingOffer(buyer, seller);
			LocalDateTime createdNow = LocalDateTime.now().minusHours(25);
			ReflectionTestUtils.setField(offer, "expiresAt", createdNow.plusHours(24));
			given(purchaseOfferRepository.findByIdForUpdate(OFFER_ID)).willReturn(Optional.of(offer));

			offerBatchExecutor.expireIfPending(OFFER_ID, LocalDateTime.now());

			assertThat(offer.getStatus()).isEqualTo(OfferStatus.EXPIRED);
			verify(paymentService).release(eq(OFFER_ID), any());
		}

		@Test
		@DisplayName("아직 만료 시각이 안 지났으면 건드리지 않는다")
		void pendingButNotYetExpired_doesNothing() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			PurchaseOffer offer = pendingOffer(buyer, seller);
			ReflectionTestUtils.setField(offer, "expiresAt", LocalDateTime.now().plusHours(1));
			given(purchaseOfferRepository.findByIdForUpdate(OFFER_ID)).willReturn(Optional.of(offer));

			offerBatchExecutor.expireIfPending(OFFER_ID, LocalDateTime.now());

			assertThat(offer.getStatus()).isEqualTo(OfferStatus.PENDING);
			verify(paymentService, never()).release(any(), any());
		}

		@Test
		@DisplayName(
				"토스 환불 실패로 결제 해제가 예외를 던지면 그대로 호출부로 전파된다"
						+ " — release() 를 메서드 마지막(알림 뒤)에 두므로, 이미 expire()·notify() 가"
						+ " 끝난 뒤에 예외가 난다. 그 앞의 변경은 REQUIRES_NEW 트랜잭션 롤백으로"
						+ " 되돌려지는데, 이 단위 테스트는 메모리 상 객체만 보므로 그 롤백 자체는 검증하지 못한다")
		void releaseThrows_propagatesExceptionAfterExpireAndNotifyAlreadyRan() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			PurchaseOffer offer = pendingOffer(buyer, seller);
			ReflectionTestUtils.setField(offer, "expiresAt", LocalDateTime.now().minusHours(1));
			given(purchaseOfferRepository.findByIdForUpdate(OFFER_ID)).willReturn(Optional.of(offer));
			willThrow(new CustomException(PaymentErrorCode.PAYMENT_409_RELEASE))
					.given(paymentService).release(eq(OFFER_ID), any());

			assertThatThrownBy(() -> offerBatchExecutor.expireIfPending(OFFER_ID, LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(PaymentErrorCode.PAYMENT_409_RELEASE);

			// release() 가 마지막이라 그 전의 offer.expire() · notify() 는 이미 실행됨(메모리상)
			assertThat(offer.getStatus()).isEqualTo(OfferStatus.EXPIRED);
			verify(notificationService).notify(any(), any(), any(), any(), any(), any(), any(), any());
		}
	}

	@Nested
	@DisplayName("탈퇴 정리 전 미리보기(isAcceptedNow)")
	class IsAcceptedNow {

		@Test
		@DisplayName("ACCEPTED 면 true — 잠그지 않고도 확인할 수 있다")
		void accepted_returnsTrue() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			PurchaseOffer offer = pendingOffer(buyer, seller);
			offer.accept(LocalDateTime.now());
			given(purchaseOfferRepository.findById(OFFER_ID)).willReturn(Optional.of(offer));

			assertThat(offerBatchExecutor.isAcceptedNow(OFFER_ID)).isTrue();
		}

		@Test
		@DisplayName("PENDING 이면 false")
		void pending_returnsFalse() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			PurchaseOffer offer = pendingOffer(buyer, seller);
			given(purchaseOfferRepository.findById(OFFER_ID)).willReturn(Optional.of(offer));

			assertThat(offerBatchExecutor.isAcceptedNow(OFFER_ID)).isFalse();
		}

		@Test
		@DisplayName("없는 제안이면 false")
		void notFound_returnsFalse() {
			given(purchaseOfferRepository.findById(OFFER_ID)).willReturn(Optional.empty());

			assertThat(offerBatchExecutor.isAcceptedNow(OFFER_ID)).isFalse();
		}
	}

	/** PENDING 제안 최소 픽스처. 이 테스트가 보는 필드(item·buyer·seller·status)만 채운다 */
	private static PurchaseOffer pendingOffer(User buyer, User seller) {
		PurchaseOffer offer = newInstance(PurchaseOffer.class);
		ReflectionTestUtils.setField(offer, "id", OFFER_ID);
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
