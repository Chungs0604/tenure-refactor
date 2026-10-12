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
import com.tenure.domain.payment.client.TossConfirmResponse;
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
import java.util.Objects;
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
	private final OfferBatchExecutor offerBatchExecutor;
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

	/**
	 * 구매 제안 / 거래 의사 전송. 결제 승인까지 같은 트랜잭션에서 끝냄.
	 *
	 * 토스페이먼츠 confirm 호출은 다른 모든 검증을 통과한 뒤, 제안을 저장하기 '직전'에 한다 —
	 * 검증 실패로 거부할 걸 먼저 돈부터 받는 순서가 되지 않도록 함
	 */
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

		// 토스페이먼츠 승인(confirm) — 이 시점까지는 DB 쓰기가 전혀 없어, 실패해도 되돌릴 게
		// 없다. 간편결제는 confirm 호출 즉시 최종 결제가 끝나므로, 이 아래부터는 "이미 돈을
		// 받은 뒤" 라는 뜻이다
		TossConfirmResponse confirmed = paymentService.confirmWithPg(
				request.getPaymentKey(), request.getOrderId(), result.getTotalPaymentAmount());

		// 이 아래부터 반환까지, 무엇이 실패하든(락 타임아웃·채팅방/알림 저장 실패·유니크 제약 등)
		// 반드시 보정 환불을 호출한 뒤 그대로 다시 던진다 — confirm 이 이미 성공해 돈이 빠져나간
		// 뒤라, 제안이 안 만들어지면 결제만 남는 상태가 된다. 이 메서드가 정상 반환한 뒤
		// 트랜잭션 커밋 자체가 실패하는 경우는 여기서 잡을 수 없다 — 그 창은 사후 대조가
		// 필요한 영역으로 남는다. confirmed 를 쓰는 코드는 전부 이 try 안에 둔다
		try {
			// 토스가 금액을 서버에서도 검증하므로 위험하진 않지만, 혹시 어긋나면 바로 알 수
			// 있게 로그만 남긴다 — amount 는 전부 FeeCalculator 의 서버 계산값이라 클라이언트가
			// 보낸 값이 그대로 쓰이는 지점은 없다
			if (!Objects.equals(confirmed.totalAmount(), result.getTotalPaymentAmount())) {
				log.error("토스 confirm 금액이 서버 계산과 다름 orderId={} expected={} actual={}",
						request.getOrderId(), result.getTotalPaymentAmount(), confirmed.totalAmount());
			}

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

			paymentService.authorize(offer, request.getPaymentMethod(), result.getTotalPaymentAmount(), confirmed, now);

			// 미판매+제안 방만 이 제안을 가격 출처로 가짐 — 판매중 채팅은 listing_id 가 그 역할을 함
			if (basis.offerType() == OfferType.PURCHASE_OFFER) {
				openOrUpdateChatRoom(item, buyer, seller, offer, now);
			}

			notificationService.notify(
					seller, NotificationSettingType.TRADE_REQUEST, NotificationCategory.ACTION_REQUIRED,
					item.getName(), tradeRequestBody(basis.offerType()),
					item.getCoverImageKey(), NotificationTargetType.OFFER, offer.getId());

			return offerMapper.toCreateResponse(offer, String.valueOf(result.getTotalPaymentAmount()));
		} catch (RuntimeException e) {
			paymentService.compensateFailedAuthorization(confirmed.paymentKey());
			throw e;
		}
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

		// 아이템에 실제로 열려 있는 판매 등록을 닫는다 — offer.getListing() 이 아니다. 미판매로
		// 받은 제안(listing=null)을 나중에 열린 등록보다 먼저 수락하면 offer.getListing() 은
		// 여전히 null 이라, 그 기준으로는 방금 열린 등록이 영원히 안 닫힌다
		itemListingRepository.findOpenByItemId(itemId)
				.ifPresent(openListing -> openListing.close(now));

		notificationService.notify(
				offer.getBuyer(), NotificationSettingType.REQUEST_STATUS, NotificationCategory.TRADE_STATUS,
				item.getName(), "제안이 수락됐어요.",
				item.getCoverImageKey(), NotificationTargetType.ORDER, order.getId());

		// 경쟁 제안 자동 취소(환불 포함)를 메서드 마지막에 둔다 — 그 전에 있는 어떤 코드든
		// 실패하면 accept() 전체(거래 생성 포함)가 롤백되는데, 경쟁 제안의 환불은 건별
		// 독립 트랜잭션으로 이미 커밋돼버려 되돌릴 수 없다. 거래 생성 자체가 확정되기
		// 직전까지 모든 코드를 통과한 뒤에만 경쟁 제안을 정리해야 한다
		int autoCanceledCount = autoCancelOtherPendingOffers(itemId, offerId, now);

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

		notificationService.notify(
				offer.getBuyer(), NotificationSettingType.REQUEST_STATUS, NotificationCategory.TRADE_STATUS,
				offer.getItem().getName(), "제안이 거절됐어요.",
				offer.getItem().getCoverImageKey(), NotificationTargetType.OFFER, offer.getId());

		// 토스 환불(release)을 메서드의 마지막에 둔다 — 이후로 실패할 수 있는 코드가 없어야,
		// 환불이 성공한 뒤 트랜잭션이 롤백돼 "돈은 돌아갔는데 상태는 그대로"가 되는 창을 없앤다
		paymentService.release(offerId, now);
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
	 * 이미 이 아이템 행을 잠근 뒤라 item → offer 잠금 순서가 지켜짐 — 여기서는 제안 행만 다시 잠근다.
	 *
	 * 건별 처리(상태 전이·결제 해제·알림)는 OfferBatchExecutor.cancelIfPending 의 독립 트랜잭션으로
	 * 넘긴다 — 토스 환불 호출이 실제로 일어나는 지금은, 한 건이 실패해도 이미 환불에 성공한
	 * 다른 건들의 커밋이 같이 날아가면 안 된다.
	 *
	 * 다만 예외를 여기서 삼키지 않고 그대로 전파한다 — 호출자가 같은 트랜잭션에서 이 메서드
	 * 뒤에 item.delete()/listing 상태 변경을 커밋하는데, 정리가 덜 끝난 채(환불 안 된 PENDING
	 * 제안이 남은 채) 그게 커밋되면 "구매자가 나중에 직접 취소해도 1회 제한이 영구 소진된
	 * 아이템" 같은 꼬인 상태가 남는다. 이미 성공해 커밋된 건들의 환불은 (REQUIRES_NEW 라)
	 * 이 예외로 롤백되지 않는다 — 그래서 아직 처리 안 된 건이 하나라도 있으면 삭제/내림
	 * 자체를 막는 게 맞다
	 */
	@Transactional(readOnly = true)
	@Loggable("판매 내림/삭제에 따른 제안 정리")
	public void cancelPendingForDelist(Long itemId, OfferCancelReason reason) {
		LocalDateTime now = LocalDateTime.now();
		// 사유는 호출 전체에서 고정이다 — 건마다 다시 판정하지 않고, 지원하지 않는 사유면
		// 상태를 하나도 안 건드린 시점에 바로 실패한다
		String notificationBody = delistCancelNotificationBody(reason);

		// id 만 가볍게 훑고, 전이는 건마다 잠금 재조회로 다시 확인함 — 그 사이 구매자가 직접
		// 취소했거나 이미 처리된 건이면 더 이상 대상이 아님
		for (Long offerId : purchaseOfferRepository.findPendingIdsByItemId(itemId)) {
			offerBatchExecutor.cancelIfPending(
					offerId, reason, now, NotificationSettingType.REQUEST_AUTO_CANCEL, notificationBody);
		}
	}

	/**
	 * cancelPendingForDelist 가 다루는 두 사유(LISTING_DELISTED·ITEM_DELETED)만 문구를 가름.
	 *
	 * default 대신 나머지 사유를 전부 나열해 switch 를 exhaustive 하게 둔다 — OfferCancelReason
	 * 에 값이 추가되는데 이 메서드를 안 고치면, default 였다면 런타임까지 숨어 있었을 실수가
	 * 컴파일 시점에 드러난다.
	 */
	private String delistCancelNotificationBody(OfferCancelReason reason) {
		return switch (reason) {
			case LISTING_DELISTED -> "판매가 종료되어 보낸 제안이 취소됐어요.";
			case ITEM_DELETED -> "아이템이 삭제되어 보낸 제안이 취소됐어요.";
			case BUYER_CANCELED, OTHER_OFFER_ACCEPTED, WITHDRAWN -> throw new IllegalStateException(
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
	 * 반환값은 탈퇴를 막아야 하는지 — 정리 도중 상대가 먼저 수락해 거래가 생긴 제안이
	 * 있었거나, 환불(결제 해제)이 하나라도 실패했으면 true 임. UserService.withdraw() 가
	 * 이 신호로 탈퇴를 막음
	 */
	@Transactional(readOnly = true)
	@Loggable("탈퇴에 따른 제안 정리")
	public boolean cancelAndRejectPendingForWithdrawal(Long userId) {
		LocalDateTime now = LocalDateTime.now();
		List<Long> offerIds = purchaseOfferRepository.findPendingIdsByBuyerOrSellerId(userId);

		// 1단계: 잠그지 않고 먼저 전부 확인만 함 — 하나라도 이미 수락된 거래가 있으면 그
		// 사실을 알기 전에 다른 제안들을 먼저 취소·환불해버리면, 탈퇴가 이 때문에 막혀도 그
		// 환불(REQUIRES_NEW 로 이미 커밋됨)은 되돌릴 수 없다. 잠금 없는 조회라 경합이 남지만,
		// 바로 이어지는 2단계의 재잠금 확인이 새로 생긴 경우까지 안전하게 걸러준다
		for (Long offerId : offerIds) {
			if (offerBatchExecutor.isAcceptedNow(offerId)) {
				log.info("탈퇴 정리 전 이미 수락된 제안을 발견함 offerId={}", offerId);
				return true;
			}
		}

		// 2단계: 통과했으면 취소·환불을 진행함. 보낸 것·받은 것을 id 오름차순으로 합쳐 훑음 —
		// 서로 PENDING 제안을 주고받은 두 사용자가 동시에 탈퇴해도 항상 같은 순서로 잠그기
		// 위함. 건별 처리는 OfferBatchExecutor.cancelForWithdrawalIfPending 의 독립
		// 트랜잭션으로 넘긴다 — 이유는 cancelPendingForDelist 주석 참고
		boolean cleanupFailed = false;
		for (Long offerId : offerIds) {
			try {
				if (offerBatchExecutor.cancelForWithdrawalIfPending(offerId, userId, now)) {
					// 1단계 이후 상대가 수락해버린 드문 경합 — 그래도 안전하게 걸러진다
					log.info("탈퇴 정리 중 먼저 수락된 제안을 발견함 offerId={}", offerId);
					return true;
				}
			} catch (Exception e) {
				// 여기서 예외를 삼키되(나머지 건은 계속 처리), 하나라도 실패하면 탈퇴 자체를
				// 막는다 — 안 그러면 익명화된 사용자에게 아직 살아있는 결제가 붙은 제안이 남는다
				log.warn("탈퇴 정리 중 오류 offerId={}", offerId, e);
				cleanupFailed = true;
			}
		}

		if (cleanupFailed) {
			log.warn("제안 정리 중 일부가 실패해 탈퇴를 막음 userId={}", userId);
			return true;
		}
		return false;
	}

	/**
	 * 24시간 무응답 자동 만료. 스케줄러가 호출함.
	 *
	 * 건별 처리는 OfferBatchExecutor.expireIfPending 의 독립 트랜잭션으로 넘긴다 — 이유는
	 * cancelPendingForDelist 주석 참고
	 */
	@Transactional(readOnly = true)
	@Loggable("구매 제안 자동 만료")
	public void expirePending(LocalDateTime now) {
		// id 만 가볍게 훑고, 전이는 건마다 잠금 재조회로 다시 확인함 — 그 사이 수락·거절·취소가
		// 끼어들었으면 더 이상 대상이 아님
		for (Long offerId : purchaseOfferRepository.findExpirableIds(now)) {
			try {
				offerBatchExecutor.expireIfPending(offerId, now);
			} catch (Exception e) {
				log.warn("자동 만료 처리 중 오류로 건너뜀 offerId={}", offerId, e);
			}
		}
	}

	/**
	 * 같은 아이템의 나머지 대기 제안을 전부 취소함. 제안자의 1회 기회는 복구됨.
	 *
	 * accept() 가 이미 이 아이템 행을 잠근 뒤라 item → offer 잠금 순서가 지켜짐.
	 *
	 * 건별 처리는 OfferBatchExecutor.cancelIfPending 의 독립 트랜잭션으로 넘긴다 — 방금 성공한
	 * accept() 의 거래 생성까지 한 트랜잭션에 묶여 있었다면, 다른 제안 하나의 환불 호출이
	 * 실패하는 순간 이미 끝난 거래까지 전부 롤백된다. 이유는 cancelPendingForDelist 주석 참고.
	 * 그래서 accept() 의 핵심 결과(거래 생성)와 분리해 건마다 독립적으로 실패하게 한다
	 */
	private int autoCancelOtherPendingOffers(Long itemId, Long acceptedOfferId, LocalDateTime now) {
		List<Long> otherIds = purchaseOfferRepository.findPendingIdsByItemId(itemId).stream()
				.filter(id -> !id.equals(acceptedOfferId))
				.toList();

		int canceled = 0;
		for (Long otherId : otherIds) {
			try {
				if (offerBatchExecutor.cancelIfPending(
						otherId, OfferCancelReason.OTHER_OFFER_ACCEPTED, now,
						NotificationSettingType.REQUEST_AUTO_CANCEL, "다른 제안이 수락되어 보낸 제안이 취소됐어요.")) {
					canceled++;
				}
			} catch (Exception e) {
				log.warn("경쟁 제안 자동 취소 중 오류로 건너뜀 offerId={}", otherId, e);
			}
		}

		return canceled;
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
