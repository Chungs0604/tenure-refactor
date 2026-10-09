package com.tenure.domain.order.service;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.domain.notification.enums.NotificationSettingType;
import com.tenure.domain.notification.enums.NotificationTargetType;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.domain.order.entity.OrderStatusHistory;
import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.order.exception.OrderErrorCode;
import com.tenure.domain.order.repository.OrderStatusHistoryRepository;
import com.tenure.domain.order.repository.TradeOrderRepository;
import com.tenure.domain.payout.entity.PayoutAccount;
import com.tenure.domain.payout.exception.PayoutErrorCode;
import com.tenure.domain.payout.repository.PayoutAccountRepository;
import com.tenure.domain.settlement.entity.Settlement;
import com.tenure.domain.settlement.repository.SettlementRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.global.exception.CustomException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매 확정·정산 배치의 건별 처리. OrderService 의 배치 메서드(autoConfirmDue, settleDue)가
 * 이 빈을 통해 호출해야 REQUIRES_NEW 가 실제로 새 트랜잭션을 연다 — 같은 빈 안에서 this 로
 * 호출하면 프록시를 거치지 않아 트랜잭션 어노테이션이 통째로 무시된다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderBatchExecutor {

	private final TradeOrderRepository tradeOrderRepository;
	private final OrderStatusHistoryRepository orderStatusHistoryRepository;
	private final SettlementRepository settlementRepository;
	private final ItemOwnershipRepository itemOwnershipRepository;
	private final PayoutAccountRepository payoutAccountRepository;
	private final NotificationService notificationService;

	/**
	 * 구매자 요청 구매 확정. 배치가 아니라 단건 호출이라 검증 실패를 그대로 던져 전파함.
	 *
	 * REQUIRES_NEW 를 쓴다 — 호출부(OrderService.confirm)가 클래스 레벨 readOnly=true 를
	 * 그대로 물려받고 있어, 일반 REQUIRED 로 두면 그 읽기전용 트랜잭션에 참여해버려
	 * findForUpdate 의 쓰기 잠금 쿼리가 "읽기 전용 트랜잭션" 오류로 실패한다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public LocalDate confirmForBuyer(Long orderId, Long buyerId, LocalDateTime now) {
		TradeOrder order = findForUpdate(orderId);
		if (!order.isBuyer(buyerId)) {
			log.info("본인 거래가 아닌데 구매확정을 처리하려 함 orderId={} userId={}", orderId, buyerId);
			throw new CustomException(OrderErrorCode.ORDER_403);
		}
		if (order.getStatus() != OrderStatus.DELIVERED) {
			log.info("배송완료 전인데 구매확정을 처리하려 함 orderId={} status={}", orderId, order.getStatus());
			throw new CustomException(OrderErrorCode.ORDER_409_STATUS);
		}
		return confirmCore(order, order.getBuyer(), now);
	}

	/**
	 * 자동 구매 확정 배치의 건별 처리. 건마다 독립된 트랜잭션으로 돌림 — 판매자가 그 사이
	 * 정산 계좌를 지운 경우 등 이 건만 실패해도 이 트랜잭션만 롤백되고 이미 처리한 다른
	 * 주문의 커밋에는 영향이 없다.
	 *
	 * 호출부(OrderService.autoConfirmDue)가 전체를 한 트랜잭션으로 묶던 예전 구조에서는,
	 * notify() 가 참여형(REQUIRED) 트랜잭션이라 안에서 예외가 나면 Spring 이 공유 트랜잭션을
	 * rollback-only 로 고정해버렸다 — 바깥 catch 가 삼켜도 배치 끝 커밋에서
	 * UnexpectedRollbackException 이 터져 이미 처리한 다른 주문까지 전부 롤려갔다.
	 * REQUIRES_NEW 로 건별 트랜잭션을 분리하면 그 건만 독립적으로 롤백된다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void confirmIfDue(Long orderId, LocalDateTime now) {
		tradeOrderRepository.findByIdForUpdate(orderId)
				.filter(order -> order.getStatus() == OrderStatus.DELIVERED
						&& !order.getAutoConfirmAt().isAfter(now))
				.ifPresent(order -> confirmCore(order, null, now));
	}

	/**
	 * 정산 배치의 건별 처리. 영업일 계산(외부 호출 가능)은 호출부(OrderService.settleDue)가
	 * 행 잠금 전에 이미 끝냈고 여기는 그 결과(이미 영업일이 지났다는 것)를 받아 상태만 바꾼다.
	 * REQUIRES_NEW 를 쓰는 이유는 confirmIfDue 와 같다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void settleIfDue(Long settlementId, Long orderId, LocalDateTime now) {
		Settlement settlement = settlementRepository.findById(settlementId).orElse(null);
		if (settlement == null || settlement.getSettledAt() != null) {
			return;
		}
		TradeOrder order = tradeOrderRepository.findByIdForUpdate(orderId).orElse(null);
		if (order == null || order.getStatus() != OrderStatus.PURCHASE_CONFIRMED) {
			return;
		}

		settlement.settle(now);
		order.settle();
		orderStatusHistoryRepository.save(OrderStatusHistory.of(order, OrderStatus.SETTLED, null, now));

		notificationService.notify(
				order.getSeller(), NotificationSettingType.TRADE_STATUS, NotificationCategory.TRADE_STATUS,
				order.getItem().getName(), "정산이 완료됐어요.",
				order.getItem().getCoverImageKey(), NotificationTargetType.ORDER, order.getId());
	}

	/**
	 * changedBy 가 null 이면 시스템(자동 확정). 소유권 승계 + 정산 생성까지 한 번에 함.
	 *
	 * 실패할 수 있는 조회(소유 구간·정산 계좌)를 전부 먼저 끝내고, 그 뒤에만 상태를 바꾼다 —
	 * 상태를 바꾼 뒤에 던지면 그 일부만 커밋되고 복구할 길이 없는 반쪽짜리 거래가 남는다.
	 * 정산 예정일(영업일 계산)은 응답 표시용일 뿐이라 여기서 계산하지 않는다 — 호출부가
	 * 이 메서드의 트랜잭션이 커밋된 뒤에 계산함
	 */
	private LocalDate confirmCore(TradeOrder order, User changedBy, LocalDateTime now) {
		Item item = order.getItem();
		ItemOwnership current = itemOwnershipRepository.findCurrent(item.getId())
				.orElseThrow(() -> {
					log.error("소유 구간 없이 거래가 진행됨 itemId={}", item.getId());
					return new CustomException(ItemErrorCode.ITEM_404);
				});
		PayoutAccount account = payoutAccountRepository.findAllOf(order.getSeller().getId()).stream()
				.findFirst()
				.orElseThrow(() -> {
					log.info("정산 계좌 없이 구매 확정이 진행됨 orderId={} sellerId={}", order.getId(), order.getSeller().getId());
					return new CustomException(PayoutErrorCode.PAYOUT_400_REQUIRED);
				});

		// 여기부터는 실패하지 않는다 — 전부 상태를 바꾸는 쪽임
		order.confirmPurchase(now);
		orderStatusHistoryRepository.save(OrderStatusHistory.of(order, OrderStatus.PURCHASE_CONFIRMED, changedBy, now));

		LocalDate confirmedDate = now.toLocalDate();
		current.close(confirmedDate);
		itemOwnershipRepository.save(ItemOwnership.openFromTrade(item, order.getBuyer(), confirmedDate, order));
		item.changeSaleStatus(SaleStatus.SOLD);

		settlementRepository.save(Settlement.of(
				order, order.getSeller(), account.getBankCode().name(), account.getAccountNumber(),
				account.getAccountHolder(), order.getPayoutAmount()));

		notificationService.notify(
				order.getSeller(), NotificationSettingType.TRADE_STATUS, NotificationCategory.TRADE_STATUS,
				order.getItem().getName(), "구매가 확정됐어요.",
				order.getItem().getCoverImageKey(), NotificationTargetType.ORDER, order.getId());

		return confirmedDate;
	}

	private TradeOrder findForUpdate(Long orderId) {
		return tradeOrderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> {
					log.info("없는 거래임 orderId={}", orderId);
					return new CustomException(OrderErrorCode.ORDER_404);
				});
	}
}
