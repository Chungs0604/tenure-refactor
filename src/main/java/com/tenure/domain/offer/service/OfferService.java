package com.tenure.domain.offer.service;

import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.address.repository.ShippingAddressRepository;
import com.tenure.domain.block.repository.UserBlockRepository;
import com.tenure.domain.chat.entity.ChatRoom;
import com.tenure.domain.chat.repository.ChatRoomRepository;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.listing.exception.ListingErrorCode;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.domain.notification.enums.NotificationSettingType;
import com.tenure.domain.notification.enums.NotificationTargetType;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.domain.offer.dto.request.OfferCreateRequest;
import com.tenure.domain.offer.dto.response.OfferAcceptResponse;
import com.tenure.domain.offer.dto.response.OfferCreateResponse;
import com.tenure.domain.offer.dto.response.OfferDetailResponse;
import com.tenure.domain.offer.dto.response.OfferPreviewResponse;
import com.tenure.domain.offer.dto.response.OfferReceivedHeaderResponse;
import com.tenure.domain.offer.dto.response.OfferReceivedListResponse;
import com.tenure.domain.offer.dto.response.OfferReceivedRowResponse;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.offer.enums.OfferCancelReason;
import com.tenure.domain.offer.enums.OfferStatus;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.offer.exception.OfferErrorCode;
import com.tenure.domain.offer.mapper.OfferMapper;
import com.tenure.domain.offer.repository.PurchaseOfferRepository;
import com.tenure.domain.order.entity.OrderShipment;
import com.tenure.domain.order.entity.OrderStatusHistory;
import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.order.repository.OrderShipmentRepository;
import com.tenure.domain.order.repository.OrderStatusHistoryRepository;
import com.tenure.domain.order.repository.TradeOrderRepository;
import com.tenure.domain.payment.entity.Payment;
import com.tenure.domain.payment.repository.PaymentRepository;
import com.tenure.domain.payment.service.PaymentService;
import com.tenure.domain.payout.entity.PayoutAccount;
import com.tenure.domain.payout.exception.PayoutErrorCode;
import com.tenure.domain.payout.repository.PayoutAccountRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CommonErrorCode;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import com.tenure.global.storage.FileStorage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매 제안 / 거래 의사의 생성부터 수락·거절·취소·만료까지.
 *
 * 미판매 아이템(PURCHASE_OFFER)과 판매 중 아이템(TRADE_INTENT)이 조건 출처만 다르고 나머지
 * 생명주기는 같아 한 서비스에서 분기함 — 전자는 아이템 기준 금액·배송지를 받고, 후자는 이미 걸린
 * 판매 조건을 그대로 씀
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OfferService {

	/** 주문 번호 접미 — 초 단위까지 겹쳐도 아이디로 구분함 */
	private static final DateTimeFormatter ORDER_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final PurchaseOfferRepository purchaseOfferRepository;
	private final UserRepository userRepository;
	private final ItemRepository itemRepository;
	private final ItemListingRepository itemListingRepository;
	private final ItemOwnershipRepository itemOwnershipRepository;
	private final ShippingAddressRepository shippingAddressRepository;
	private final UserBlockRepository userBlockRepository;
	private final ChatRoomRepository chatRoomRepository;
	private final PaymentRepository paymentRepository;
	private final PayoutAccountRepository payoutAccountRepository;
	private final TradeOrderRepository tradeOrderRepository;
	private final OrderShipmentRepository orderShipmentRepository;
	private final OrderStatusHistoryRepository orderStatusHistoryRepository;
	private final PaymentService paymentService;
	private final NotificationService notificationService;
	private final OfferMapper offerMapper;
	private final FileStorage fileStorage;

	/** 결제 금액 미리보기 */
	@Loggable("구매 제안 미리보기")
	public OfferPreviewResponse preview(Long itemId, Integer offerPriceParam) {
		Item item = findActiveItem(itemId);
		// 판매중이면 열린 등록의 조건, 미판매면 쿼리로 받은 금액을 씀
		OfferBasis basis = resolveBasis(item, itemListingRepository.findOpenByItemId(itemId), offerPriceParam);

		// 거래 수수료는 소유자(판매자) 등급 기준임
		// "금액" 자체가 아니라 금액을 계산하기 위한 원재료(조건)
		BigDecimal feeRate = FeeCalculator.rateOf(basis.seller().getGrade());

		FeeCalculator.Result result = FeeCalculator.calculate(
				basis.offerPrice(), basis.shippingFee(),
				feeRate, basis.feeBearer());

		// 금액은 전부 화면 표시용 문자열로 바꿔 넘김
		return offerMapper.toPreviewResponse(
				String.valueOf(basis.offerPrice()),
				String.valueOf(basis.shippingFee()),
				feeRate,
				String.valueOf(result.getFeeAmount()),
				String.valueOf(result.getBuyerFeeAmount()),
				String.valueOf(result.getSellerFeeAmount()),
				String.valueOf(result.getTotalPaymentAmount()),
				String.valueOf(result.getExpectedPayoutAmount()));
	}

	/** 구매 제안 / 거래 의사 전송. 결제 승인까지 같은 트랜잭션에서 끝냄 */
	@Transactional
	@Loggable("구매 제안 전송")
	public OfferCreateResponse create(Long buyerId, Long itemId, OfferCreateRequest request) {
		LocalDateTime now = LocalDateTime.now();

		// accept() 와 같은 순서(item → offer)로 아이템을 먼저 잠가, 동시에 들어온 다른 제안
		// 경로와 SOLD 판정이 커밋된 값 기준으로 직렬화되게 함
		Item item = itemRepository.findActiveForUpdate(itemId)
				.orElseThrow(() -> {
					log.info("없거나 삭제된 아이템임 itemId={}", itemId);
					return new CustomException(ItemErrorCode.ITEM_404);
				});

		// 판매 완료된 상품엔 제안을 보낼 수 없음
		if (item.getSaleStatus() == SaleStatus.SOLD) {
			log.info("판매 완료된 상품에 제안을 시도함 itemId={}", itemId);
			throw new CustomException(ListingErrorCode.LISTING_409_SOLD);
		}

		Optional<ItemListing> openListing = itemListingRepository.findOpenByItemId(itemId);

		// 요청한 제안 유형이 아이템 현재 상태와 안 맞으면 막음 — 판매중엔 거래 의사만, 미판매엔 구매 제안만
		OfferType expectedType = openListing.isPresent() ? OfferType.TRADE_INTENT : OfferType.PURCHASE_OFFER;

		if (request.getOfferType() != expectedType) {
			log.info("아이템 상태와 맞지 않는 제안 유형임 itemId={} requested={} expected={}",
					itemId, request.getOfferType(), expectedType);
			throw new CustomException(OfferErrorCode.OFFER_400_NOT_ALLOWED);
		}

		// 구매 제안을 막아 둔 아이템. offerAllowed 는 미판매 CTA 전용이라 거래 의사에는 걸지 않음 —
		// 가격 유효성(미판매인데 금액이 없음)보다 먼저 봐야 더 정확한 사유로 거부함
		if (expectedType == OfferType.PURCHASE_OFFER && !Boolean.TRUE.equals(item.getOfferAllowed())) {
			log.info("구매 제안을 받지 않는 상품임 itemId={}", itemId);
			throw new CustomException(OfferErrorCode.OFFER_400_NOT_ALLOWED);
		}

		// 판매중이면 열린 등록의 조건, 미판매면 쿼리로 받은 금액을 씀
		// "금액" 자체가 아니라 금액을 계산하기 위한 원재료(조건)
		OfferBasis basis = resolveBasis(item, openListing, request.getOfferPrice());

		User buyer = findUser(buyerId);
		User seller = basis.seller();

		// 본인 상품에는 제안할 수 없음
		if (buyer.getId().equals(seller.getId())) {
			log.info("본인 상품에 제안을 시도함 itemId={} userId={}", itemId, buyerId);
			throw new CustomException(OfferErrorCode.OFFER_400_SELF);
		}

		// 차단 관계면 양방향 중 하나만 있어도 막음
		if (userBlockRepository.existsMutualBlock(buyer.getId(), seller.getId())) {
			log.info("차단 관계인 상대에게 제안을 시도함 buyerId={} sellerId={}", buyer.getId(), seller.getId());
			throw new CustomException(OfferErrorCode.OFFER_403_BLOCKED);
		}

		// 1회 제한은 미판매 구매 제안에만 걸림 — 거래 의사는 몇 번이든 다시 보낼 수 있음
		if (basis.offerType() == OfferType.PURCHASE_OFFER) {
			requireNotAlreadyUsed(itemId, buyerId);
		}

		ShippingAddress address = shippingAddressRepository.findByIdAndUserId(request.getShippingAddressId(), buyerId)
				.orElseThrow(() -> {
					log.info("본인 배송지가 아니거나 없는 배송지임 shippingAddressId={} buyerId={}",
							request.getShippingAddressId(), buyerId);
					return new CustomException(OfferErrorCode.OFFER_400_ADDRESS_REQUIRED);
				});

		// 거래 수수료
		BigDecimal feeRate = FeeCalculator.rateOf(seller.getGrade());

		FeeCalculator.Result result = FeeCalculator.calculate(
				basis.offerPrice(), basis.shippingFee(),
				feeRate, basis.feeBearer());

		PurchaseOffer offer;
		try {
			// IDENTITY 전략이라 save 호출 시점에 바로 INSERT 됨 — uk_purchase_offer_limit 위반이면
			// 여기서 바로 던져짐(동시에 보낸 두 번째 요청)
			offer = purchaseOfferRepository.save(
					PurchaseOffer.create(
						basis.listing(), item, buyer, seller, basis.offerType(),
						basis.offerPrice(), basis.shippingFee(), basis.feeBearer(), feeRate,
						result.getBuyerFeeAmount(), result.getSellerFeeAmount(),
						result.getTotalPaymentAmount(), result.getExpectedPayoutAmount(),
						request.getBuyerRequest(), address, now
					)
			);
		} catch (DataIntegrityViolationException e) {
			// uk_purchase_offer_limit 은 PURCHASE_OFFER 행만 걸림(V20 함수형 인덱스) — TRADE_INTENT
			// 에서 난 제약 위반을 같은 뜻으로 오역하지 않도록 유형으로 좁힘
			if (basis.offerType() != OfferType.PURCHASE_OFFER) {
				throw e;
			}
			log.info("동시 요청으로 1회 제한 유니크 제약에 걸림 itemId={} buyerId={}", itemId, buyerId);
			throw new CustomException(OfferErrorCode.OFFER_409_ALREADY_USED);
		}

		paymentService.authorize(offer, request.getPaymentMethod(), result.getTotalPaymentAmount(), now);

		// 미판매+제안 방만 이 제안을 가격 출처로 가짐 — 판매중 채팅은 listing_id 가 그 역할을 함
		if (basis.offerType() == OfferType.PURCHASE_OFFER) {
			openOrUpdateChatRoom(item, buyer, seller, offer, now);
		}

		notificationService.notify(
				seller, NotificationSettingType.TRADE_REQUEST, NotificationCategory.ACTION_REQUIRED,
				item.getName(), tradeRequestBody(basis.offerType()),
				item.getCoverImageKey(), NotificationTargetType.OFFER, offer.getId());

		return offerMapper.toCreateResponse(offer, String.valueOf(result.getTotalPaymentAmount()));
	}

	/** 제안서 상세. 구매자·판매자 시점에 따라 결제/정산 블록이 갈림 */
	@Loggable("제안서 상세 조회")
	public OfferDetailResponse getDetail(Long userId, Long offerId) {
		PurchaseOffer offer = findDetail(offerId);
		// 호출자가 구매자인지 판매자인지로 금액 블록이 갈림
		boolean isBuyerView = requireParticipant(offer, userId);

		Item item = offer.getItem();
		var itemSummary = offerMapper.toItemSummaryResponse(item, fileStorage.toUrl(item.getCoverImageKey()));
		var shipping = offerMapper.toShippingResponse(offer);
		// 구매자 시점이면 상대는 판매자, 판매자 시점이면 상대는 구매자임
		User counterpart = isBuyerView ? offer.getSeller() : offer.getBuyer();

		// 결제 수단 표시는 구매자 시점에만 필요함
		Payment payment = paymentRepository.findByOfferId(offerId).orElse(null);

		return offerMapper.toDetailResponse(
				offer, itemSummary,
				String.valueOf(offer.getOfferPrice()),
				String.valueOf(offer.getShippingFee()),
				String.valueOf(offer.getFeeAmount()),
				isBuyerView ? String.valueOf(offer.getTotalPaymentAmount()) : null,
				isBuyerView ? null : String.valueOf(offer.getExpectedPayoutAmount()),
				shipping,
				isBuyerView ? null : payoutAccountDisplay(offer.getSeller().getId()),
				offerMapper.toCounterpartResponse(counterpart),
				isBuyerView && payment != null ? payment.getMethod() : null);
	}

	/** 제안 취소 (구매자). 기회는 복구되지 않음 */
	@Transactional
	@Loggable("구매 제안 취소")
	public void cancel(Long buyerId, Long offerId) {
		LocalDateTime now = LocalDateTime.now();
		// 이 제안 행만 잠금 — 같은 제안에 수락·거절·만료가 동시에 들어오는 경합을 직렬화함
		PurchaseOffer offer = findForUpdate(offerId);

		if (!offer.isBuyer(buyerId)) {
			log.info("본인 제안이 아닌데 취소를 시도함 offerId={} userId={}", offerId, buyerId);
			throw new CustomException(OfferErrorCode.OFFER_403);
		}

		requireRespondable(offer, now);

		offer.cancel(OfferCancelReason.BUYER_CANCELED, now);
		paymentService.release(offerId, now);
	}

	/**
	 * 제안 수락 (판매자) → 거래 생성 + 나머지 대기 제안 자동 취소.
	 *
	 * 잠금 순서를 item → offer 로 둠 — ListingService.changeSaleStatus 쪽도 같은 순서로 아이템을
	 * 먼저 잠그므로, 두 경로가 서로 다른 순서로 걸어 교착하는 일이 없음
	 */
	@Transactional
	@Loggable("구매 제안 수락")
	public OfferAcceptResponse accept(Long sellerId, Long offerId) {
		LocalDateTime now = LocalDateTime.now();

		Long itemId = purchaseOfferRepository.findItemIdByOfferId(offerId)
				.orElseThrow(() -> {
					log.info("없는 제안임 offerId={}", offerId);
					return new CustomException(OfferErrorCode.OFFER_404);
				});

		// 아이템을 먼저 잠금 — 같은 아이템의 다른 제안이 먼저 수락됐는지 커밋된 값으로 재확인함
		Item item = itemRepository.findActiveForUpdate(itemId)
				.orElseThrow(() -> {
					log.info("없거나 삭제된 아이템임 itemId={}", itemId);
					return new CustomException(ItemErrorCode.ITEM_404);
				});
		if (item.getSaleStatus() == SaleStatus.SOLD) {
			log.info("다른 제안이 먼저 수락되어 이미 판매 완료됨 itemId={}", itemId);
			throw new CustomException(ListingErrorCode.LISTING_409_SOLD);
		}

		// 이어서 이 제안 행을 잠금
		PurchaseOffer offer = findForUpdate(offerId);

		if (!offer.isSeller(sellerId)) {
			log.info("본인 제안이 아닌데 수락을 시도함 offerId={} userId={}", offerId, sellerId);
			throw new CustomException(OfferErrorCode.OFFER_403);
		}

		requireRespondable(offer, now);

		// 정산계좌가 없으면 수락해도 정산할 곳이 없음 — 판매 전환을 거친 제안은 이미 확인됐으므로 미판매 쪽만 걸림
		if (offer.getListing() == null && !payoutAccountRepository.existsByUserId(sellerId)) {
			log.info("정산 계좌 없이 미판매 제안을 수락하려 함 sellerId={}", sellerId);
			throw new CustomException(PayoutErrorCode.PAYOUT_400_REQUIRED);
		}

		// 결제 확정 + 제안 상태 전이
		Payment payment = paymentService.capture(offerId, now);
		offer.accept(now);

		// 거래 생성 — 금액·조건은 제안 스냅샷을 그대로 옮김
		TradeOrder order = tradeOrderRepository.save(
				TradeOrder.createFromOffer(offer, generateOrderNo(offerId, now), now));
		// 배송지도 제안이 생성 시점에 떠 둔 스냅샷을 그대로 다시 옮김
		orderShipmentRepository.save(OrderShipment.fromOffer(order, offer));
		// 거래 타임라인의 첫 단계 — 시스템이 올렸으니 changedBy 는 null
		orderStatusHistoryRepository.save(OrderStatusHistory.of(order, OrderStatus.PAYMENT_COMPLETED, null, now));
		payment.linkOrder(order);

		// 결제 완료 시점에 아이템이 판매 완료로 전이함
		item.changeSaleStatus(SaleStatus.SOLD);
		if (offer.getListing() != null && offer.getListing().isOpen()) {
			offer.getListing().close(now);
		}

		int autoCanceledCount = autoCancelOtherPendingOffers(itemId, offerId, now);

		notificationService.notify(
				offer.getBuyer(), NotificationSettingType.REQUEST_STATUS, NotificationCategory.TRADE_STATUS,
				item.getName(), "제안이 수락됐어요.",
				item.getCoverImageKey(), NotificationTargetType.ORDER, order.getId());

		return offerMapper.toAcceptResponse(order, autoCanceledCount);
	}

	/** 제안 거절 (판매자). 거절한 제안은 다시 수락할 수 없음 */
	@Transactional
	@Loggable("구매 제안 거절")
	public void reject(Long sellerId, Long offerId) {
		LocalDateTime now = LocalDateTime.now();
		// 이 제안 행만 잠금 — 같은 제안에 수락·취소·만료가 동시에 들어오는 경합을 직렬화함
		PurchaseOffer offer = findForUpdate(offerId);

		if (!offer.isSeller(sellerId)) {
			log.info("본인 제안이 아닌데 거절을 시도함 offerId={} userId={}", offerId, sellerId);
			throw new CustomException(OfferErrorCode.OFFER_403);
		}

		if (!offer.isPending()) {
			log.info("이미 응답했거나 만료된 제안을 거절하려 함 offerId={}", offerId);
			throw new CustomException(OfferErrorCode.OFFER_409_NOT_PENDING);
		}

		offer.reject(now);
		paymentService.release(offerId, now);

		notificationService.notify(
				offer.getBuyer(), NotificationSettingType.REQUEST_STATUS, NotificationCategory.TRADE_STATUS,
				offer.getItem().getName(), "제안이 거절됐어요.",
				offer.getItem().getCoverImageKey(), NotificationTargetType.OFFER, offer.getId());
	}

	/** 아이템별 받은 제안·요청 목록 (판매자) */
	@Loggable("받은 제안 목록 조회")
	public OfferReceivedListResponse getReceivedList(Long sellerId, Long itemId) {
		LocalDateTime now = LocalDateTime.now();
		Item item = findActiveItem(itemId);
		Optional<ItemListing> openListing = itemListingRepository.findOpenByItemId(itemId);

		// 소유자 판정도 resolveBasis 와 같은 규칙 — 판매중이면 등록의 판매자, 미판매면 현재 소유자
		User owner = openListing.map(ItemListing::getSeller).orElseGet(() -> currentOwner(itemId));
		if (!owner.getId().equals(sellerId)) {
			log.info("본인 아이템이 아닌데 받은 제안을 조회함 itemId={} userId={}", itemId, sellerId);
			throw new CustomException(ItemErrorCode.ITEM_403);
		}

		// 헤더 문구(거래 의사/구매 제안)를 가르는 값
		OfferType offerType = openListing.isPresent() ? OfferType.TRADE_INTENT : OfferType.PURCHASE_OFFER;

		// 배치가 아직 안 돈 만료 건은 조회 시점에 한 번 더 걸러냄
		List<PurchaseOffer> offers = purchaseOfferRepository.findPendingByItemId(itemId).stream()
				.filter(offer -> !offer.isExpired(now))
				.toList();

		// price 는 판매중일 때만 채움 — 미판매면 아이템에 가격 자체가 없음
		OfferReceivedHeaderResponse header = offerMapper.toReceivedHeaderResponse(
				item, fileStorage.toUrl(item.getCoverImageKey()),
				openListing.map(listing -> String.valueOf(listing.getPrice())).orElse(null));

		// 행마다 제안자 프로필 사진을 URL 로 바꿔 실음
		List<OfferReceivedRowResponse> rows = offers.stream()
				.map(offer -> offerMapper.toReceivedRowResponse(
						offer,
						offerMapper.toBuyerSummaryResponse(
								offer.getBuyer(),
								offer.getBuyer().getProfileImageKey() == null
										? null
										: fileStorage.toUrl(offer.getBuyer().getProfileImageKey())),
						String.valueOf(offer.getOfferPrice())))
				.toList();

		return offerMapper.toReceivedListResponse(header, offerType, rows.size(), rows);
	}

	/**
	 * 판매 내림/아이템 삭제로 남은 대기 제안 정리. 수락 경로의 자동 취소와 대칭임 — 제안자
	 * 잘못이 아니라 1회 제한에서 제외하고, 결제 승인을 해제한 뒤 알림을 보냄.
	 *
	 * 호출자(ListingService.changeSaleStatus·closeOnSaleItemsForWithdrawal, ItemService.delete)가
	 * 이미 이 아이템 행을 잠근 뒤라 item → offer 잠금 순서가 지켜짐 — 여기서는 제안 행만 다시 잠근다
	 */
	@Transactional
	@Loggable("판매 내림/삭제에 따른 제안 정리")
	public void cancelPendingForDelist(Long itemId, OfferCancelReason reason) {
		LocalDateTime now = LocalDateTime.now();

		// id 만 가볍게 훑고, 전이는 건마다 잠금 재조회로 다시 확인함 — 그 사이 구매자가 직접
		// 취소했거나 이미 처리된 건이면 더 이상 대상이 아님
		for (Long offerId : purchaseOfferRepository.findPendingIdsByItemId(itemId)) {
			relockIfStillPending(offerId).ifPresent(offer -> {
				offer.cancel(reason, now);
				paymentService.release(offer.getId(), now);

				notificationService.notify(
						offer.getBuyer(), NotificationSettingType.REQUEST_AUTO_CANCEL, NotificationCategory.TRADE_STATUS,
						offer.getItem().getName(), delistCancelNotificationBody(reason),
						offer.getItem().getCoverImageKey(), NotificationTargetType.OFFER, offer.getId());
			});
		}
	}

	/** cancelPendingForDelist 가 다루는 두 사유(LISTING_DELISTED·ITEM_DELETED)만 문구를 가름 */
	private String delistCancelNotificationBody(OfferCancelReason reason) {
		return switch (reason) {
			case LISTING_DELISTED -> "판매가 종료되어 보낸 제안이 취소됐어요.";
			case ITEM_DELETED -> "아이템이 삭제되어 보낸 제안이 취소됐어요.";
			default -> throw new IllegalArgumentException(
					"cancelPendingForDelist 에서 다루지 않는 취소 사유임: " + reason);
		};
	}

	/**
	 * 회원탈퇴 시 남은 대기 제안 정리. 보낸 것·받은 것 가리지 않고 시스템 취소(WITHDRAWN)로
	 * 남겨 결제를 해제하고 상대에게 알림을 보냄.
	 *
	 * 받은 것도 reject() 대신 cancel() 을 씀 — reject() 로 끝내면 아무 잘못 없는 구매자가
	 * 그 아이템에 영구히 재제안하지 못하게 된다(유니크 인덱스가 REJECTED 는 면제해주지 않음).
	 *
	 * 반환값은 정리 도중 상대가 먼저 수락해 거래가 생긴 제안이 있었는지 — 있었으면
	 * UserService.withdraw() 가 탈퇴를 막아야 하는 신호임
	 */
	@Transactional
	@Loggable("탈퇴에 따른 제안 정리")
	public boolean cancelAndRejectPendingForWithdrawal(Long userId) {
		LocalDateTime now = LocalDateTime.now();
		boolean concurrentTradeFound = false;

		// 보낸 것·받은 것을 id 오름차순으로 합쳐 훑음 — 서로 PENDING 제안을 주고받은 두
		// 사용자가 동시에 탈퇴해도 항상 같은 순서로 잠그기 위함. id 만 가볍게 읽고 건마다
		// findForUpdate 로 재확인함 — 그 사이 상대가 수락했으면 ACCEPTED 로 정확히 보임
		for (Long offerId : purchaseOfferRepository.findPendingIdsByBuyerOrSellerId(userId)) {
			PurchaseOffer offer = findForUpdate(offerId);

			if (offer.isPending()) {
				offer.cancel(OfferCancelReason.WITHDRAWN, now);
				// 임시로 잡아 둔 결제 승인을 풀어 상대가 확정하지 못하게 함
				paymentService.release(offer.getId(), now);

				if (offer.isBuyer(userId)) {
					// 받는 쪽이던 판매자에게 제안이 사라졌음을 알림
					notificationService.notify(
							offer.getSeller(), NotificationSettingType.REQUEST_AUTO_CANCEL, NotificationCategory.TRADE_STATUS,
							offer.getItem().getName(), "상대가 탈퇴해 받은 제안이 취소됐어요.",
							offer.getItem().getCoverImageKey(), NotificationTargetType.OFFER, offer.getId());
				} else {
					// 보낸 쪽이던 구매자에게 취소됐음을 알림
					notificationService.notify(
							offer.getBuyer(), NotificationSettingType.REQUEST_AUTO_CANCEL, NotificationCategory.TRADE_STATUS,
							offer.getItem().getName(), "상대가 탈퇴해 보낸 제안이 취소됐어요.",
							offer.getItem().getCoverImageKey(), NotificationTargetType.OFFER, offer.getId());
				}
			} else if (offer.getStatus() == OfferStatus.ACCEPTED) {
				// 정리 도중 상대가 먼저 수락해 거래가 생김 — 탈퇴를 이대로 진행하면 안 됨
				log.info("탈퇴 정리 중 먼저 수락된 제안을 발견함 offerId={}", offerId);
				concurrentTradeFound = true;
			}
			// REJECTED·CANCELED·EXPIRED 는 거래가 생기지 않은 상태라 그냥 넘어감
		}

		return concurrentTradeFound;
	}

	/** 24시간 무응답 자동 만료. 스케줄러가 호출함 */
	@Transactional
	@Loggable("구매 제안 자동 만료")
	public void expirePending(LocalDateTime now) {
		// id 만 가볍게 훑고, 전이는 건마다 잠금 재조회로 다시 확인함 — 그 사이 수락·거절·취소가
		// 끼어들었으면 더 이상 대상이 아님
		for (Long offerId : purchaseOfferRepository.findExpirableIds(now)) {
			relockIfStillPending(offerId)
					.filter(offer -> offer.isExpired(now))
					.ifPresent(offer -> {
						offer.expire(now);
						paymentService.release(offer.getId(), now);

						notificationService.notify(
								offer.getBuyer(), NotificationSettingType.REQUEST_AUTO_CANCEL, NotificationCategory.TRADE_STATUS,
								offer.getItem().getName(), "응답이 없어 보낸 제안이 만료됐어요.",
								offer.getItem().getCoverImageKey(), NotificationTargetType.OFFER, offer.getId());
					});
		}
	}

	/**
	 * 같은 아이템의 나머지 대기 제안을 전부 취소함. 제안자의 1회 기회는 복구됨.
	 *
	 * accept() 가 이미 이 아이템 행을 잠근 뒤라 item → offer 잠금 순서가 지켜짐 — 여기서는
	 * 제안 행만 다시 잠근다(건별로 구매자의 취소·거절과 경합할 수 있어 재확인이 필요함)
	 */
	private int autoCancelOtherPendingOffers(Long itemId, Long acceptedOfferId, LocalDateTime now) {
		List<Long> otherIds = purchaseOfferRepository.findPendingIdsByItemId(itemId).stream()
				.filter(id -> !id.equals(acceptedOfferId))
				.toList();

		int canceled = 0;
		for (Long otherId : otherIds) {
			Optional<PurchaseOffer> other = relockIfStillPending(otherId);
			if (other.isEmpty()) {
				continue;
			}
			PurchaseOffer offer = other.get();
			offer.cancel(OfferCancelReason.OTHER_OFFER_ACCEPTED, now);
			paymentService.release(offer.getId(), now);
			canceled++;

			notificationService.notify(
					offer.getBuyer(), NotificationSettingType.REQUEST_AUTO_CANCEL, NotificationCategory.TRADE_STATUS,
					offer.getItem().getName(), "다른 제안이 수락되어 보낸 제안이 취소됐어요.",
					offer.getItem().getCoverImageKey(), NotificationTargetType.OFFER, offer.getId());
		}

		return canceled;
	}

	/** 잠금 재조회 후 아직 PENDING 일 때만 돌려줌 — 그 사이 다른 응답이 먼저 끝났으면 빈 값임 */
	private Optional<PurchaseOffer> relockIfStillPending(Long offerId) {

		//findForUpdate로 가지고 온 offer는 비관적 락이 걸린 상태
		PurchaseOffer offer = findForUpdate(offerId);
		return offer.isPending() ? Optional.of(offer) : Optional.empty();
	}

	/**
	 * PENDING 이 아니거나 유효 시간이 지났으면 응답할 수 없음.
	 *
	 * 상태를 먼저 봄 — 이미 ACCEPTED·REJECTED·CANCELED 로 끝난 제안은 생성된 지 오래라
	 * 시간상으로도 만료 조건을 만족하는데, 그런 경우까지 EXPIRED 로 잘못 답하면 안 됨.
	 * 만료는 '아직 PENDING 인데 배치가 늦어 시간만 지난' 경우에만 해당함
	 */
	private void requireRespondable(PurchaseOffer offer, LocalDateTime now) {
		if (!offer.isPending()) {
			log.info("이미 응답했거나 취소된 제안에 다시 응답하려 함 offerId={}", offer.getId());
			throw new CustomException(OfferErrorCode.OFFER_409_NOT_PENDING);
		}
		if (offer.isExpired(now)) {
			log.info("유효 시간이 지난 제안에 응답하려 함 offerId={}", offer.getId());
			throw new CustomException(OfferErrorCode.OFFER_410_EXPIRED);
		}
	}

	/** 구매자·판매자 둘 다 아니면 열람 권한이 없음. 반환값은 구매자 시점인지 여부임 */
	private boolean requireParticipant(PurchaseOffer offer, Long userId) {
		if (offer.isBuyer(userId)) {
			return true;
		}
		if (offer.isSeller(userId)) {
			return false;
		}
		log.info("제안과 관련 없는 사용자가 상세를 조회함 offerId={} userId={}", offer.getId(), userId);
		throw new CustomException(OfferErrorCode.OFFER_403);
	}

	/** 이미 쓴 기회가 있으면 막음. 시스템 취소(자동 취소·판매 내림)는 제외하고 봄 */
	private void requireNotAlreadyUsed(Long itemId, Long buyerId) {
		purchaseOfferRepository.findLatestByItemIdAndBuyerId(itemId, buyerId).ifPresent(latest -> {
			boolean exempt = latest.getStatus() == OfferStatus.CANCELED
					&& latest.getCancelReason() != OfferCancelReason.BUYER_CANCELED;
			if (!exempt) {
				log.info("이미 1회 제한을 쓴 아이템에 다시 제안하려 함 itemId={} buyerId={}", itemId, buyerId);
				throw new CustomException(OfferErrorCode.OFFER_409_ALREADY_USED);
			}
		});
	}

	/**
	 * 제안 조건의 출처를 가름. 판매 중이면 열린 판매 등록, 미판매면 요청받은 금액 — 둘 다 아이템
	 * 하나를 두고 배타적으로 갈림
	 */
	private OfferBasis resolveBasis(Item item, Optional<ItemListing> openListing, Integer offerPriceParam) {
		if (openListing.isPresent()) {
			ItemListing listing = openListing.get();
			return new OfferBasis(
					OfferType.TRADE_INTENT, listing, listing.getSeller(),
					listing.getPrice(), listing.getShippingFee(), listing.getFeeBearer());
		}

		// 미판매 구매 제안은 배송비 0원·판매자 전액 부담으로 고정됨 — 고를 화면도 선택지도 없음
		if (offerPriceParam == null) {
			log.info("미판매 구매 제안인데 제안 금액이 없음 itemId={}", item.getId());
			throw new CustomException(CommonErrorCode.BAD_REQUEST);
		}
		return new OfferBasis(
				OfferType.PURCHASE_OFFER, null, currentOwner(item.getId()),
				offerPriceParam, 0, FeeBearer.SELLER);
	}

	/** 판매 중이면 가리킬 열린 판매 등록이 있고, 미판매면 현재 소유자가 그 역할을 함 */
	private record OfferBasis(
			OfferType offerType, ItemListing listing, User seller,
			Integer offerPrice, Integer shippingFee, FeeBearer feeBearer
	) {
	}

	/** 미판매+제안 방을 찾아 열거나, 재제안이면 가격 출처를 최신 제안으로 갱신함 */
	private void openOrUpdateChatRoom(Item item, User buyer, User seller, PurchaseOffer offer, LocalDateTime now) {
		chatRoomRepository.findByItemIdAndBuyerIdAndSellerId(item.getId(), buyer.getId(), seller.getId())
				.ifPresentOrElse(
						room -> room.updateOfferPointer(offer),
						() -> chatRoomRepository.save(ChatRoom.openForOffer(item, buyer, seller, offer, now)));
	}

	/** 정산 계좌가 없으면 표시할 것이 없음 — 판매자가 아직 계좌를 등록 안 한 경우일 수 있음 */
	private String payoutAccountDisplay(Long sellerId) {
		return payoutAccountRepository.findPrimaryOf(sellerId)
				.map(this::formatAccount)
				.orElse(null);
	}

	private String formatAccount(PayoutAccount account) {
		return account.getBankCode().getBankName() + " " + account.getAccountNumber();
	}

	/**
	 * 주문 번호. 시각 뒤에 제안 id 를 그대로 붙임 — id 자체가 전역 유일이라 겹칠 일이 없음.
	 * 나머지 연산으로 자릿수를 줄이면 같은 초에 수락된 두 제안의 id 가 그 자릿수만큼 차이 날
	 * 때 uk_trade_order_no 위반으로 이어질 수 있어 그대로 둠
	 */
	private String generateOrderNo(Long offerId, LocalDateTime now) {
		return now.format(ORDER_NO_FORMAT) + offerId;
	}

	private String tradeRequestBody(OfferType offerType) {
		return offerType == OfferType.TRADE_INTENT ? "거래 의사를 보냈어요." : "구매 제안을 보냈어요.";
	}

	private Item findActiveItem(Long itemId) {
		return itemRepository.findActive(itemId)
				.orElseThrow(() -> {
					log.info("없거나 삭제된 아이템임 itemId={}", itemId);
					return new CustomException(ItemErrorCode.ITEM_404);
				});
	}

	private User currentOwner(Long itemId) {
		return itemOwnershipRepository.findCurrent(itemId)
				.map(ItemOwnership::getOwner)
				.orElseThrow(() -> {
					log.info("소유 구간이 없는 아이템임 itemId={}", itemId);
					return new CustomException(ItemErrorCode.ITEM_404);
				});
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> {
					log.info("없는 사용자임 userId={}", userId);
					return new CustomException(UserErrorCode.USER_404);
				});
	}

	private PurchaseOffer findDetail(Long offerId) {
		return purchaseOfferRepository.findDetailById(offerId)
				.orElseThrow(() -> {
					log.info("없는 제안임 offerId={}", offerId);
					return new CustomException(OfferErrorCode.OFFER_404);
				});
	}

	// 조회 offer에 X-Lock(쓰기 잠금)을 검 (비관적 락)
	private PurchaseOffer findForUpdate(Long offerId) {
		return purchaseOfferRepository.findByIdForUpdate(offerId)
				.orElseThrow(() -> {
					log.info("없는 제안임 offerId={}", offerId);
					return new CustomException(OfferErrorCode.OFFER_404);
				});
	}
}
