package com.tenure.domain.offer.service;

import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.domain.notification.enums.NotificationSettingType;
import com.tenure.domain.notification.enums.NotificationTargetType;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.offer.enums.OfferCancelReason;
import com.tenure.domain.offer.enums.OfferStatus;
import com.tenure.domain.offer.exception.OfferErrorCode;
import com.tenure.domain.offer.repository.PurchaseOfferRepository;
import com.tenure.domain.payment.service.PaymentService;
import com.tenure.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제안 자동 정리(판매 내림·탈퇴·만료·경쟁 제안 취소) 배치의 건별 처리. OfferService 의 배치
 * 메서드가 이 빈을 통해 호출해야 REQUIRES_NEW 가 실제로 새 트랜잭션을 연다 — 같은 빈 안에서
 * this 로 호출하면 프록시를 거치지 않아 트랜잭션 어노테이션이 통째로 무시된다.
 *
 * release() 가 토스페이먼츠 환불을 실제로 호출하게 되면서(간편결제는 confirm 즉시 결제가 끝나
 * "임시 승인 해제"가 아니라 진짜 환불이다) 건별 트랜잭션 분리가 그냥 안전망이 아니라 필수가
 * 됐다 — 한 건의 환불 호출이 실패해도 그 건만 롤백되고, 이미 토스에 환불 요청을 보내 성공한
 * 다른 건들은 그대로 커밋돼 있어야 한다. 전부 한 트랜잭션에 묶어두면, 뒤쪽 건에서 예외가 나는
 * 순간 Spring 이 트랜잭션을 rollback-only 로 고정해 앞서 이미 환불된 건까지 전부 롤백되는데,
 * 그 환불은 토스 쪽에서 이미 끝난 뒤라 되돌릴 수 없다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OfferBatchExecutor {

	private final PurchaseOfferRepository purchaseOfferRepository;
	private final PaymentService paymentService;
	private final NotificationService notificationService;

	/**
	 * 판매 내림/삭제·경쟁 제안 수락으로 남은 대기 제안을 취소함. 제안자 잘못이 아닌 사유라
	 * 1회 제한에서 제외된다.
	 *
	 * @return 실제로 취소했는지 — 재잠금 사이 이미 끝난 건이면 false
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean cancelIfPending(
			Long offerId, OfferCancelReason reason, LocalDateTime now,
			NotificationSettingType notificationSettingType, String notificationBody) {
		return relockIfStillPending(offerId)
				.map(offer -> {
					offer.cancel(reason, now);
					notificationService.notify(
							offer.getBuyer(), notificationSettingType, NotificationCategory.TRADE_STATUS,
							offer.getItem().getName(), notificationBody,
							offer.getItem().getCoverImageKey(), NotificationTargetType.OFFER, offer.getId());
					// 토스 환불(release)을 마지막에 둔다 — 그 이후로 이 메서드 안에서 실패할 수
					// 있는 코드가 없어야, 환불 성공 뒤 이 트랜잭션이 롤백돼 "돈은 돌아갔는데
					// 상태는 그대로"가 되는 창을 없앤다
					paymentService.release(offerId, now);
					return true;
				})
				.orElse(false);
	}

	/** 24시간 무응답 자동 만료. 사유·문구가 고정이라 매개변수로 받지 않는다 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void expireIfPending(Long offerId, LocalDateTime now) {
		relockIfStillPending(offerId)
				.filter(offer -> offer.isExpired(now))
				.ifPresent(offer -> {
					offer.expire(now);
					notificationService.notify(
							offer.getBuyer(), NotificationSettingType.REQUEST_AUTO_CANCEL, NotificationCategory.TRADE_STATUS,
							offer.getItem().getName(), "응답이 없어 보낸 제안이 만료됐어요.",
							offer.getItem().getCoverImageKey(), NotificationTargetType.OFFER, offer.getId());
					// release 를 마지막에 두는 이유는 cancelIfPending 주석 참고
					paymentService.release(offerId, now);
				});
	}

	/**
	 * 탈퇴 정리 실행 전 미리보기용 — 잠그지 않고 현재 상태만 본다. 바로 이어지는
	 * cancelForWithdrawalIfPending 이 재확인해 안전하게 걸러주므로 잠금은 필요 없다.
	 *
	 * {@code REQUIRES_NEW} 로 새 트랜잭션을 연다 — 호출부(OfferService.
	 * cancelAndRejectPendingForWithdrawal)가 이미 열어 둔 {@code withdraw()} 의 트랜잭션에
	 * 그냥 참여하면, MySQL REPEATABLE READ 라 그 트랜잭션이 처음 읽은 시점의 스냅샷을 그대로
	 * 보게 된다 — "지금" 수락됐는지가 아니라 "탈퇴 처리 시작 시점"에 수락됐는지를 보는
	 * 꼴이라, 이 미리보기가 사실상 의미 없어진다. 새 트랜잭션이어야 최신 커밋을 본다
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public boolean isAcceptedNow(Long offerId) {
		return purchaseOfferRepository.findById(offerId)
				.map(offer -> offer.getStatus() == OfferStatus.ACCEPTED)
				.orElse(false);
	}

	/**
	 * 회원탈퇴에 따른 정리. 보낸 것·받은 것 가리지 않고 WITHDRAWN 으로 취소함 — reject() 가
	 * 아니라 cancel() 을 쓰는 이유는 OfferService.cancelAndRejectPendingForWithdrawal 참고.
	 *
	 * @return 재잠금 사이 상대가 먼저 수락해 거래가 생겼는지 — 호출부가 탈퇴를 막아야 하는 신호임
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean cancelForWithdrawalIfPending(Long offerId, Long withdrawingUserId, LocalDateTime now) {
		PurchaseOffer offer = findForUpdate(offerId);

		if (!offer.isPending()) {
			// ACCEPTED 만 거래 발견으로 본다 — REJECTED·CANCELED·EXPIRED 는 거래가 생기지 않은 상태
			return offer.getStatus() == OfferStatus.ACCEPTED;
		}

		offer.cancel(OfferCancelReason.WITHDRAWN, now);

		if (offer.isBuyer(withdrawingUserId)) {
			notificationService.notify(
					offer.getSeller(), NotificationSettingType.REQUEST_AUTO_CANCEL, NotificationCategory.TRADE_STATUS,
					offer.getItem().getName(), "상대가 탈퇴해 받은 제안이 취소됐어요.",
					offer.getItem().getCoverImageKey(), NotificationTargetType.OFFER, offer.getId());
		} else {
			notificationService.notify(
					offer.getBuyer(), NotificationSettingType.REQUEST_AUTO_CANCEL, NotificationCategory.TRADE_STATUS,
					offer.getItem().getName(), "상대가 탈퇴해 보낸 제안이 취소됐어요.",
					offer.getItem().getCoverImageKey(), NotificationTargetType.OFFER, offer.getId());
		}
		// release 를 마지막에 두는 이유는 cancelIfPending 주석 참고
		paymentService.release(offerId, now);
		return false;
	}

	/** 재잠금 후 아직 PENDING 일 때만 돌려줌 — 그 사이 다른 응답이 먼저 끝났으면 빈 값임 */
	private Optional<PurchaseOffer> relockIfStillPending(Long offerId) {
		PurchaseOffer offer = findForUpdate(offerId);
		return offer.isPending() ? Optional.of(offer) : Optional.empty();
	}

	private PurchaseOffer findForUpdate(Long offerId) {
		return purchaseOfferRepository.findByIdForUpdate(offerId)
				.orElseThrow(() -> {
					log.info("없는 제안임 offerId={}", offerId);
					return new CustomException(OfferErrorCode.OFFER_404);
				});
	}
}
