package com.tenure.domain.order.service;

import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.domain.notification.enums.NotificationSettingType;
import com.tenure.domain.notification.enums.NotificationTargetType;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.offer.repository.PurchaseOfferRepository;
import com.tenure.domain.order.dto.response.CancelResponse;
import com.tenure.domain.order.dto.response.ConfirmResponse;
import com.tenure.domain.order.dto.response.CourierResponse;
import com.tenure.domain.order.dto.response.DeliverResponse;
import com.tenure.domain.order.dto.response.OrderDetailResponse;
import com.tenure.domain.order.dto.response.OrderItemSummaryResponse;
import com.tenure.domain.order.dto.response.OrderPartySummaryResponse;
import com.tenure.domain.order.dto.response.OrderPaymentResponse;
import com.tenure.domain.order.dto.response.OrderPurchaseRowResponse;
import com.tenure.domain.order.dto.response.OrderSalesRowResponse;
import com.tenure.domain.order.dto.response.OrderSettlementResponse;
import com.tenure.domain.order.dto.response.OrderShipmentResponse;
import com.tenure.domain.order.dto.response.OrderStatusHistoryResponse;
import com.tenure.domain.order.dto.response.ShipmentUpdateResponse;
import com.tenure.domain.order.entity.OrderShipment;
import com.tenure.domain.order.entity.OrderStatusHistory;
import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.order.enums.CourierCode;
import com.tenure.domain.order.enums.OrderFeedTab;
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.order.enums.OrderViewerRole;
import com.tenure.domain.order.exception.OrderErrorCode;
import com.tenure.domain.order.mapper.OrderMapper;
import com.tenure.domain.order.repository.OrderShipmentRepository;
import com.tenure.domain.order.repository.OrderStatusHistoryRepository;
import com.tenure.domain.order.repository.TradeOrderRepository;
import com.tenure.domain.payment.entity.Payment;
import com.tenure.domain.payment.repository.PaymentRepository;
import com.tenure.domain.payment.service.PaymentService;
import com.tenure.domain.settlement.entity.Settlement;
import com.tenure.domain.settlement.repository.SettlementRepository;
import com.tenure.domain.payout.enums.BankCode;
import com.tenure.domain.user.entity.User;
import com.tenure.global.exception.CustomException;
import com.tenure.global.holiday.BusinessDayCalculator;
import com.tenure.global.logging.Loggable;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.storage.FileStorage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래 조회·배송·구매확정·취소. 결제 확정과 거래 생성 자체는 OfferService.accept() 가 한다 —
 * 여기는 '결제 완료' 이후의 진행만 다룬다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

	/** 배송 완료 +72h 후 자동 구매 확정 */
	private static final long AUTO_CONFIRM_HOURS = 72;

	/** 날짜 경계(정산 영업일 계산 등)가 JVM 기본 시간대에 흔들리지 않도록 명시적으로 고정함 */
	private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

	/** 구매 확정일 + 2영업일이 정산 예정일 */
	private static final int SETTLEMENT_BUSINESS_DAYS = 2;

	private static final Set<OrderStatus> IN_PROGRESS_STATUSES = EnumSet.of(
			OrderStatus.PAYMENT_COMPLETED, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.PURCHASE_CONFIRMED);

	private final TradeOrderRepository tradeOrderRepository;
	private final OrderShipmentRepository orderShipmentRepository;
	private final OrderStatusHistoryRepository orderStatusHistoryRepository;
	private final SettlementRepository settlementRepository;
	private final PurchaseOfferRepository purchaseOfferRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentService paymentService;
	private final NotificationService notificationService;
	private final FileStorage fileStorage;
	private final OrderMapper orderMapper;
	private final TrackingUrlResolver trackingUrlResolver;
	private final BusinessDayCalculator businessDayCalculator;
	private final OrderBatchExecutor orderBatchExecutor;

	/** 거래 상세. settlement 은 SELLER 시점에만 채움 */
	@Loggable("거래 상세 조회")
	public OrderDetailResponse getDetail(Long userId, Long orderId) {
		TradeOrder order = tradeOrderRepository.findDetailById(orderId)
				.orElseThrow(() -> {
					log.info("없는 거래임 orderId={}", orderId);
					return new CustomException(OrderErrorCode.ORDER_404);
				});
		OrderViewerRole viewerRole = resolveViewerRole(order, userId);

		OrderShipment shipment = orderShipmentRepository.findByOrderId(orderId).orElse(null);
		List<OrderStatusHistoryResponse> histories =
				orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(orderId).stream()
						.map(orderMapper::toStatusHistoryResponse)
						.toList();
		Payment payment = paymentRepository.findByOfferId(order.getOffer().getId()).orElse(null);
		Settlement settlement = viewerRole == OrderViewerRole.SELLER
				? settlementRepository.findByOrderId(orderId).orElse(null)
				: null;

		OrderItemSummaryResponse itemSummary = orderMapper.toItemSummaryResponse(
				order.getItem(), fileStorage.toUrl(order.getItem().getCoverImageKey()));
		OrderPartySummaryResponse buyer = orderMapper.toPartySummaryResponse(
				order.getBuyer(), profileImageUrl(order.getBuyer()));
		OrderPartySummaryResponse seller = orderMapper.toPartySummaryResponse(
				order.getSeller(), profileImageUrl(order.getSeller()));
		OrderShipmentResponse shipmentResponse = shipment == null ? null
				: orderMapper.toShipmentResponse(
						shipment, trackingUrlResolver.resolve(shipment.getCourierCode(), shipment.getTrackingNo()));
		OrderPaymentResponse paymentResponse = payment == null ? null : orderMapper.toPaymentResponse(payment);
		OrderSettlementResponse settlementResponse = settlement == null ? null
				: orderMapper.toSettlementResponse(settlement, parseBankCode(settlement.getBankCode()));

		return orderMapper.toDetailResponse(
				order, viewerRole, itemSummary, buyer, seller, histories,
				order.getOffer().getBuyerRequest(), shipmentResponse,
				String.valueOf(order.getItemPrice()), String.valueOf(order.getShippingFee()),
				String.valueOf(order.getFeeAmount()), String.valueOf(order.getTotalPaymentAmount()),
				String.valueOf(order.getPayoutAmount()), paymentResponse, settlementResponse);
	}

	/** 택배사 목록. 값이 늘어도 앱 배포와 무관하게 내려가야 해서 API 로 둔다 */
	public List<CourierResponse> getCouriers() {
		return Arrays.stream(CourierCode.values())
				.map(code -> orderMapper.toCourierResponse(code, courierLabel(code)))
				.toList();
	}

	/** 운송장 번호 입력 → 상품 발송. 배송 완료 전까지 재입력 가능함 */
	@Transactional
	@Loggable("운송장 번호 입력")
	public ShipmentUpdateResponse updateShipment(Long sellerId, Long orderId, String courierCodeRaw, String trackingNo) {
		LocalDateTime now = LocalDateTime.now(ZONE_SEOUL);
		TradeOrder order = tradeOrderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> {
					log.info("없는 거래임 orderId={}", orderId);
					return new CustomException(OrderErrorCode.ORDER_404_TRACKING);
				});

		if (!order.isSeller(sellerId)) {
			log.info("본인 거래가 아닌데 운송장을 입력하려 함 orderId={} userId={}", orderId, sellerId);
			throw new CustomException(OrderErrorCode.ORDER_403);
		}
		if (order.getStatus() != OrderStatus.PAYMENT_COMPLETED && order.getStatus() != OrderStatus.SHIPPED) {
			log.info("발송할 수 없는 단계에서 운송장을 입력하려 함 orderId={} status={}", orderId, order.getStatus());
			throw new CustomException(OrderErrorCode.ORDER_409_STATUS);
		}
		// enum 으로 안 받는 이유는 ShipmentUpdateRequest 의 클래스 주석과 같다 — 이 엔드포인트의
		// 에러 표면을 ORDER_400_TRACKING 하나로 유지하기 위함
		CourierCode courierCode = parseCourierCode(courierCodeRaw);
		if (courierCode == null || trackingNo == null || !trackingNo.matches("\\d+")) {
			throw new CustomException(OrderErrorCode.ORDER_400_TRACKING);
		}

		OrderShipment shipment = orderShipmentRepository.findByOrderId(orderId)
				.orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_404_TRACKING));

		boolean firstShipment = order.getStatus() == OrderStatus.PAYMENT_COMPLETED;
		shipment.updateTracking(courierCode, trackingNo, now);
		order.ship();

		if (firstShipment) {
			orderStatusHistoryRepository.save(OrderStatusHistory.of(order, OrderStatus.SHIPPED, order.getSeller(), now));
			notificationService.notify(
					order.getBuyer(), NotificationSettingType.TRADE_STATUS, NotificationCategory.TRADE_STATUS,
					order.getItem().getName(), "상품이 발송됐어요.",
					order.getItem().getCoverImageKey(), NotificationTargetType.ORDER, order.getId());
		}

		return orderMapper.toShipmentUpdateResponse(order.getStatus());
	}

	/** 배송 완료 처리(판매자). 72시간 뒤 자동 구매 확정을 건다 */
	@Transactional
	@Loggable("배송 완료 처리")
	public DeliverResponse deliver(Long sellerId, Long orderId) {
		LocalDateTime now = LocalDateTime.now(ZONE_SEOUL);
		TradeOrder order = findForUpdate(orderId);

		if (!order.isSeller(sellerId)) {
			log.info("본인 거래가 아닌데 배송완료를 처리하려 함 orderId={} userId={}", orderId, sellerId);
			throw new CustomException(OrderErrorCode.ORDER_403);
		}
		if (order.getStatus() != OrderStatus.SHIPPED) {
			log.info("발송 전인데 배송완료를 처리하려 함 orderId={} status={}", orderId, order.getStatus());
			throw new CustomException(OrderErrorCode.ORDER_409_STATUS);
		}

		LocalDateTime autoConfirmAt = now.plusHours(AUTO_CONFIRM_HOURS);
		order.deliver(autoConfirmAt);

		orderShipmentRepository.findByOrderId(orderId).ifPresent(shipment -> shipment.markDelivered(now));
		orderStatusHistoryRepository.save(OrderStatusHistory.of(order, OrderStatus.DELIVERED, order.getSeller(), now));

		notificationService.notify(
				order.getBuyer(), NotificationSettingType.TRADE_STATUS, NotificationCategory.TRADE_STATUS,
				order.getItem().getName(), "배송이 완료됐어요.",
				order.getItem().getCoverImageKey(), NotificationTargetType.ORDER, order.getId());

		return orderMapper.toDeliverResponse(order.getStatus(), autoConfirmAt);
	}

	/** 구매 확정(구매자) → 소유권 승계 + 정산 생성 */
	@Loggable("구매 확정")
	public ConfirmResponse confirm(Long buyerId, Long orderId) {
		LocalDateTime now = LocalDateTime.now(ZONE_SEOUL);
		LocalDate confirmedDate = orderBatchExecutor.confirmForBuyer(orderId, buyerId, now);
		// 정산 예정일은 응답 표시용일 뿐이라 상태 변경 커밋 뒤에 계산함 — confirmForBuyer 가
		// 이미 자기 트랜잭션을 커밋하고 반환한 상태라 아래 호출은 그 바깥에서 돈다
		LocalDate scheduledAt = businessDayCalculator.plusBusinessDays(confirmedDate, SETTLEMENT_BUSINESS_DAYS);
		return orderMapper.toConfirmResponse(OrderStatus.PURCHASE_CONFIRMED, scheduledAt);
	}

	/** 배송 완료 +72h 지난 거래를 자동 확정. 스케줄러가 호출함 */
	@Transactional(readOnly = true)
	@Loggable("자동 구매 확정 배치")
	public void autoConfirmDue(LocalDateTime now) {
		for (Long orderId : tradeOrderRepository.findAutoConfirmableIds(now)) {
			try {
				// 건별로 독립된 트랜잭션(orderBatchExecutor.confirmIfDue)에 넘긴다 — 판매자가
				// 그 사이 정산 계좌를 지운 경우 등 이 건만 실패해도 그 트랜잭션만 롤백되고
				// 이미 처리한 다른 주문의 커밋에는 영향이 없다. 자세한 이유는
				// OrderBatchExecutor.confirmIfDue 주석 참고
				orderBatchExecutor.confirmIfDue(orderId, now);
			} catch (Exception e) {
				log.warn("자동 구매 확정 중 오류로 건너뜀 orderId={}", orderId, e);
			}
		}
	}

	/** 상품 발송 전 거래 취소(구매자·판매자 둘 다 가능) → 환불 */
	@Transactional
	@Loggable("거래 취소")
	public CancelResponse cancel(Long userId, Long orderId) {
		LocalDateTime now = LocalDateTime.now(ZONE_SEOUL);
		TradeOrder order = findForUpdate(orderId);

		boolean isBuyer = order.isBuyer(userId);
		if (!isBuyer && !order.isSeller(userId)) {
			log.info("본인과 관련 없는 거래를 취소하려 함 orderId={} userId={}", orderId, userId);
			throw new CustomException(OrderErrorCode.ORDER_403);
		}
		if (order.getStatus() != OrderStatus.PAYMENT_COMPLETED) {
			log.info("발송 이후에 거래 취소를 시도함 orderId={} status={}", orderId, order.getStatus());
			throw new CustomException(OrderErrorCode.ORDER_409_CANCEL_WINDOW);
		}

		paymentService.refund(order.getOffer().getId(), now);
		order.cancel(now);
		// 판매로 올라간 적 있어도 listing 은 닫힌 뒤 다시 열지 않는다 — 재판매는
		// 판매 전환을 다시 거쳐 새 listing 을 만들어야 함. 그래서 미판매로 보낸다(ON_SALE 아님)
		order.getItem().changeSaleStatus(SaleStatus.NOT_FOR_SALE);

		User changedBy = isBuyer ? order.getBuyer() : order.getSeller();
		orderStatusHistoryRepository.save(OrderStatusHistory.of(order, OrderStatus.CANCELED, changedBy, now));

		User counterpart = isBuyer ? order.getSeller() : order.getBuyer();
		notificationService.notify(
				counterpart, NotificationSettingType.TRADE_STATUS, NotificationCategory.TRADE_STATUS,
				order.getItem().getName(), "거래가 취소됐어요.",
				order.getItem().getCoverImageKey(), NotificationTargetType.ORDER, order.getId());

		return orderMapper.toCancelResponse(order.getStatus(), now);
	}

	/** 정산 예정일이 지난 건을 정산 완료로 넘긴다. 스케줄러가 호출함 */
	@Transactional(readOnly = true)
	@Loggable("정산 배치")
	public void settleDue(LocalDateTime now) {
		LocalDate today = now.toLocalDate();
		for (Settlement settlement : settlementRepository.findAllBySettledAtIsNull()) {
			// 영업일 계산(공휴일 조회, 캐시 미스면 외부 HTTP)을 트랜잭션·행 잠금 밖에서 먼저
			// 끝낸다 — 건마다 반복되는 이 호출이 느려져도 쓰기 트랜잭션을 길게 물고 있지 않음
			LocalDate scheduledAt = businessDayCalculator.plusBusinessDays(
					settlement.getOrder().getConfirmedAt().toLocalDate(), SETTLEMENT_BUSINESS_DAYS);
			if (scheduledAt.isAfter(today)) {
				continue;
			}
			try {
				// 건별로 독립된 트랜잭션(orderBatchExecutor.settleIfDue)에 넘긴다 — 이유는
				// autoConfirmDue 와 같다. OrderBatchExecutor.settleIfDue 주석 참고
				orderBatchExecutor.settleIfDue(settlement.getId(), settlement.getOrder().getId(), now);
			} catch (Exception e) {
				log.warn("정산 처리 중 오류로 건너뜀 settlementId={}", settlement.getId(), e);
			}
		}
	}

	/** 판매 내역 4탭. PENDING 은 아이템 단위로 묶고, 전체 탭은 그 묶음과 거래를 시간순으로 섞는다 */
	@Loggable("판매 내역 조회")
	public CursorSlice<OrderSalesRowResponse> getSales(Long sellerId, OrderFeedTab tab, Cursor cursor, int size) {
		return switch (tab) {
			case PENDING -> {
				List<PendingGroup> groups = filterGroupsAfterCursor(pendingGroupsForSeller(sellerId), cursor);
				yield CursorSlice.of(groups, size, this::cursorOfGroup).map(this::toSalesRow);
			}
			case IN_PROGRESS -> fetchSalesOrders(sellerId, IN_PROGRESS_STATUSES, cursor, size);
			case DONE -> fetchSalesOrders(sellerId, OrderStatus.FINISHED, cursor, size);
			case ALL -> mergeSalesAll(sellerId, cursor, size);
		};
	}

	/** 구매 내역 4탭. 판매 내역과 대칭이나 아이템 단위 집계가 없다(구매자는 아이템당 1건) */
	@Loggable("구매 내역 조회")
	public CursorSlice<OrderPurchaseRowResponse> getPurchases(Long buyerId, OrderFeedTab tab, Cursor cursor, int size) {
		return switch (tab) {
			case PENDING -> {
				List<PurchaseOffer> offers = filterOffersAfterCursor(
						purchaseOfferRepository.findAllPendingByBuyer(buyerId), cursor);
				yield CursorSlice.of(offers, size, o -> Cursor.of(o.getCreatedAt(), o.getId()))
						.map(this::toPurchaseRow);
			}
			case IN_PROGRESS -> fetchPurchaseOrders(buyerId, IN_PROGRESS_STATUSES, cursor, size);
			case DONE -> fetchPurchaseOrders(buyerId, OrderStatus.FINISHED, cursor, size);
			case ALL -> mergePurchasesAll(buyerId, cursor, size);
		};
	}

	// ── 상세 조회 보조 ───────────────────────────────────────────────

	private TradeOrder findForUpdate(Long orderId) {
		return tradeOrderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> {
					log.info("없는 거래임 orderId={}", orderId);
					return new CustomException(OrderErrorCode.ORDER_404);
				});
	}

	private OrderViewerRole resolveViewerRole(TradeOrder order, Long userId) {
		if (order.isBuyer(userId)) {
			return OrderViewerRole.BUYER;
		}
		if (order.isSeller(userId)) {
			return OrderViewerRole.SELLER;
		}
		log.info("거래와 관련 없는 사용자가 상세를 조회함 orderId={} userId={}", order.getId(), userId);
		throw new CustomException(OrderErrorCode.ORDER_403);
	}

	private String profileImageUrl(User user) {
		return user.getProfileImageKey() == null ? null : fileStorage.toUrl(user.getProfileImageKey());
	}

	/** 잘못된 값이면 null — 호출부가 ORDER_400_TRACKING 하나로 묶어 던진다 */
	private CourierCode parseCourierCode(String raw) {
		if (raw == null) {
			return null;
		}
		try {
			return CourierCode.valueOf(raw);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * settlement 는 append-only 라 과거 행의 bankCode 문자열이 영원히 남는다 — 그 상수가 나중에
	 * 바뀌거나 지워지면 valueOf() 가 터진다. 그런 거래의 상세 조회까지 500 으로 죽이지 않도록
	 * 방어한다
	 */
	private BankCode parseBankCode(String raw) {
		try {
			return BankCode.valueOf(raw);
		} catch (IllegalArgumentException e) {
			log.warn("정산 계좌의 은행 코드를 해석할 수 없음 bankCode={}", raw);
			return null;
		}
	}

	private String courierLabel(CourierCode code) {
		return switch (code) {
			case CJ_LOGISTICS -> "CJ대한통운";
			case KOREA_POST -> "우체국택배";
			case GS_POSTBOX -> "GS Postbox";
			case CU_CONVENIENCE -> "CU 편의점택배";
		};
	}

	// ── 판매 내역 ────────────────────────────────────────────────────

	private CursorSlice<OrderSalesRowResponse> fetchSalesOrders(
			Long sellerId, Set<OrderStatus> statuses, Cursor cursor, int size
	) {
		List<TradeOrder> orders = tradeOrderRepository.findAllBySellerForFeed(sellerId, statuses, cursor, size);
		return CursorSlice.of(orders, size, o -> Cursor.of(o.getCreatedAt(), o.getId())).map(this::toSalesRow);
	}

	/**
	 * 전체 탭 — PENDING 묶음(bound 된 전수 조회) + 거래(DB 커서 size+1) 를 시간순으로 머지한다.
	 * 각 소스가 자기 쪽 진짜 top-(size+1) 을 주므로, 머지 후 앞에서 size+1 개만 잘라도 전체
	 * 합집합의 top-(size+1) 과 같다
	 */
	private CursorSlice<OrderSalesRowResponse> mergeSalesAll(Long sellerId, Cursor cursor, int size) {
		List<PendingGroup> groups = filterGroupsAfterCursor(pendingGroupsForSeller(sellerId), cursor);
		List<TradeOrder> orders = tradeOrderRepository.findAllBySellerForFeed(sellerId, null, cursor, size);

		List<FeedEntry> a = groups.stream().map(g -> new FeedEntry(cursorOfGroup(g), g)).toList();
		List<FeedEntry> b = orders.stream().map(o -> new FeedEntry(Cursor.of(o.getCreatedAt(), o.getId()), o)).toList();
		List<FeedEntry> merged = mergeDesc(a, b).stream().limit(size + 1L).toList();

		return CursorSlice.of(merged, size, FeedEntry::cursor)
				.map(entry -> entry.payload() instanceof PendingGroup g
						? toSalesRow(g)
						: toSalesRow((TradeOrder) entry.payload()));
	}

	private OrderSalesRowResponse toSalesRow(PendingGroup group) {
		PurchaseOffer offer = group.latestOffer();
		OrderPartySummaryResponse counterpart = orderMapper.toPartySummaryResponse(
				offer.getBuyer(), profileImageUrl(offer.getBuyer()));
		OrderItemSummaryResponse item = orderMapper.toItemSummaryResponse(
				offer.getItem(), fileStorage.toUrl(offer.getItem().getCoverImageKey()));
		return orderMapper.toSalesRowFromOffer(
				offer, group.itemId(), group.count(), String.valueOf(offer.getOfferPrice()), counterpart, item);
	}

	private OrderSalesRowResponse toSalesRow(TradeOrder order) {
		OrderPartySummaryResponse counterpart = orderMapper.toPartySummaryResponse(
				order.getBuyer(), profileImageUrl(order.getBuyer()));
		OrderItemSummaryResponse item = orderMapper.toItemSummaryResponse(
				order.getItem(), fileStorage.toUrl(order.getItem().getCoverImageKey()));
		return orderMapper.toSalesRowFromOrder(order, String.valueOf(order.getPayoutAmount()), counterpart, item);
	}

	/**
	 * 이 판매자의 PENDING 제안을 아이템 단위로 묶는다. PENDING 은 24시간 만료 정책으로 bound 된
	 * 집합이라(trade_order 처럼 수년치가 쌓이지 않음) 전수 조회 후 메모리에서 묶어도 된다
	 */
	private List<PendingGroup> pendingGroupsForSeller(Long sellerId) {
		List<PurchaseOffer> all = purchaseOfferRepository.findAllPendingBySeller(sellerId);
		return all.stream()
				.collect(Collectors.groupingBy(o -> o.getItem().getId()))
				.entrySet().stream()
				// all 이 이미 (createdAt desc, id desc) 로 정렬돼 있어, 묶음의 첫 원소가 그 아이템의
				// 최신 제안이다 — 전체 스트림을 desc 로 훑으므로 같은 itemId 를 처음 만난 자리가
				// 가장 최신이다
				.map(entry -> new PendingGroup(entry.getKey(), entry.getValue().size(), entry.getValue().get(0)))
				.sorted(Comparator
						.comparing((PendingGroup g) -> g.latestOffer().getCreatedAt())
						.thenComparing(g -> g.latestOffer().getId())
						.reversed())
				.toList();
	}

	private List<PendingGroup> filterGroupsAfterCursor(List<PendingGroup> groups, Cursor cursor) {
		if (cursor == null) {
			return groups;
		}
		return groups.stream()
				.filter(g -> isAfterCursor(g.latestOffer().getCreatedAt(), g.latestOffer().getId(), cursor))
				.toList();
	}

	private Cursor cursorOfGroup(PendingGroup group) {
		return Cursor.of(group.latestOffer().getCreatedAt(), group.latestOffer().getId());
	}

	// ── 구매 내역 ────────────────────────────────────────────────────

	private CursorSlice<OrderPurchaseRowResponse> fetchPurchaseOrders(
			Long buyerId, Set<OrderStatus> statuses, Cursor cursor, int size
	) {
		List<TradeOrder> orders = tradeOrderRepository.findAllByBuyerForFeed(buyerId, statuses, cursor, size);
		return CursorSlice.of(orders, size, o -> Cursor.of(o.getCreatedAt(), o.getId())).map(this::toPurchaseRow);
	}

	/**
	 * 전체 탭 — 양쪽 다 DB 커서로 size+1 을 받는다. 구매 쪽은 상태 무관 전체 이력이라
	 * 판매 쪽(PENDING bound)과 달리 offer 쪽도 쌓일수록 커지므로 진짜 페이지네이션이 필요하다
	 */
	private CursorSlice<OrderPurchaseRowResponse> mergePurchasesAll(Long buyerId, Cursor cursor, int size) {
		List<PurchaseOffer> offers = purchaseOfferRepository.findAllByBuyerForFeed(buyerId, cursor, size);
		List<TradeOrder> orders = tradeOrderRepository.findAllByBuyerForFeed(buyerId, null, cursor, size);

		List<FeedEntry> a = offers.stream().map(o -> new FeedEntry(Cursor.of(o.getCreatedAt(), o.getId()), o)).toList();
		List<FeedEntry> b = orders.stream().map(o -> new FeedEntry(Cursor.of(o.getCreatedAt(), o.getId()), o)).toList();
		List<FeedEntry> merged = mergeDesc(a, b).stream().limit(size + 1L).toList();

		return CursorSlice.of(merged, size, FeedEntry::cursor)
				.map(entry -> entry.payload() instanceof PurchaseOffer o
						? toPurchaseRow(o)
						: toPurchaseRow((TradeOrder) entry.payload()));
	}

	private OrderPurchaseRowResponse toPurchaseRow(PurchaseOffer offer) {
		OrderPartySummaryResponse counterpart = orderMapper.toPartySummaryResponse(
				offer.getSeller(), profileImageUrl(offer.getSeller()));
		OrderItemSummaryResponse item = orderMapper.toItemSummaryResponse(
				offer.getItem(), fileStorage.toUrl(offer.getItem().getCoverImageKey()));
		return orderMapper.toPurchaseRowFromOffer(offer, String.valueOf(offer.getOfferPrice()), counterpart, item);
	}

	private OrderPurchaseRowResponse toPurchaseRow(TradeOrder order) {
		OrderPartySummaryResponse counterpart = orderMapper.toPartySummaryResponse(
				order.getSeller(), profileImageUrl(order.getSeller()));
		OrderItemSummaryResponse item = orderMapper.toItemSummaryResponse(
				order.getItem(), fileStorage.toUrl(order.getItem().getCoverImageKey()));
		return orderMapper.toPurchaseRowFromOrder(order, String.valueOf(order.getTotalPaymentAmount()), counterpart, item);
	}

	private List<PurchaseOffer> filterOffersAfterCursor(List<PurchaseOffer> offers, Cursor cursor) {
		if (cursor == null) {
			return offers;
		}
		return offers.stream()
				.filter(o -> isAfterCursor(o.getCreatedAt(), o.getId(), cursor))
				.toList();
	}

	private boolean isAfterCursor(LocalDateTime createdAt, Long id, Cursor cursor) {
		LocalDateTime cutoff = cursor.asDateTime();
		if (createdAt.isBefore(cutoff)) {
			return true;
		}
		return createdAt.isEqual(cutoff) && id < cursor.getId();
	}

	// ── 두 소스 머지(둘 다 createdAt desc, id desc 로 이미 정렬돼 있음) ──────────────

	private List<FeedEntry> mergeDesc(List<FeedEntry> a, List<FeedEntry> b) {
		List<FeedEntry> merged = new ArrayList<>(a.size() + b.size());
		int i = 0;
		int j = 0;
		while (i < a.size() && j < b.size()) {
			FeedEntry x = a.get(i);
			FeedEntry y = b.get(j);
			if (compareDesc(x.cursor(), y.cursor()) <= 0) {
				merged.add(x);
				i++;
			} else {
				merged.add(y);
				j++;
			}
		}
		merged.addAll(a.subList(i, a.size()));
		merged.addAll(b.subList(j, b.size()));
		return merged;
	}

	/** x 가 y 보다 최신이면(먼저 와야 하면) 음수 */
	private int compareDesc(Cursor x, Cursor y) {
		int cmp = y.asDateTime().compareTo(x.asDateTime());
		if (cmp != 0) {
			return cmp;
		}
		return y.getId().compareTo(x.getId());
	}

	/** PENDING 제안의 아이템 단위 묶음 */
	private record PendingGroup(Long itemId, int count, PurchaseOffer latestOffer) {
	}

	/** 커서 머지용 포장. payload 는 PendingGroup·PurchaseOffer·TradeOrder 중 하나 */
	private record FeedEntry(Cursor cursor, Object payload) {
	}
}
