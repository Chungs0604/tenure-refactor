package com.tenure.domain.listing.service;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.listing.dto.request.ListingCreateRequest;
import com.tenure.domain.listing.dto.request.ListingItemRequest;
import com.tenure.domain.listing.dto.response.ListingIdResponse;
import com.tenure.domain.listing.dto.response.SaleStatusResponse;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.listing.entity.ListingFeaturedPost;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.listing.exception.ListingErrorCode;
import com.tenure.domain.listing.mapper.ListingMapper;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.listing.repository.ListingFeaturedPostRepository;
import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.domain.notification.enums.NotificationSettingType;
import com.tenure.domain.notification.enums.NotificationTargetType;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.domain.offer.enums.OfferCancelReason;
import com.tenure.domain.offer.service.OfferService;
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.order.repository.TradeOrderRepository;
import com.tenure.domain.payout.exception.PayoutErrorCode;
import com.tenure.domain.payout.repository.PayoutAccountRepository;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.exception.PostErrorCode;
import com.tenure.domain.post.repository.PostRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.wishlist.repository.WishlistRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tenure.domain.item.dto.request.ItemForm;
import com.tenure.domain.item.service.ItemFormApplier;

/**
 * 판매 전환·수정·상태 변경·대표 OOTD 교체.
 *
 * 판매 전환은 한 트랜잭션에서 다섯을 쓴다 — item · item_listing · item_measurement ·
 * item_condition · listing_featured_post. 화면이 한 폼·한 CTA 라서다(3342:32943).
 * 프론트가 아이템 수정을 먼저 부르는 방식은 중간에 실패하면 아이템만 바뀌고 판매 등록이 안 된
 * 상태가 남아 택하지 않았다.
 *
 * 판매 상태는 item 이 들고 있다(DEC-026) — 판매 등록이 없는 아이템도 '미판매'·'판매 완료' 를
 * 표시해야 하기 때문이다. 그래서 상태 전이는 늘 item 을 함께 쓴다.
 *
 * 예외를 던지기 전에 판단 근거를 남긴다 — ErrorCode 자체는 ServiceLoggingAspect 가 찍는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingService {

	/** 대표 OOTD 개수. 화면 문구가 '대표 OOTD (1개 이상)' 과 '최대 5개' 다 */
	private static final int FEATURED_MIN = 1;
	private static final int FEATURED_MAX = 5;

	private final ItemListingRepository itemListingRepository;
	private final ListingFeaturedPostRepository listingFeaturedPostRepository;
	private final ItemRepository itemRepository;
	private final ItemFormApplier itemFormApplier;
	private final ItemOwnershipRepository itemOwnershipRepository;
	private final PayoutAccountRepository payoutAccountRepository;
	private final PostRepository postRepository;
	private final TradeOrderRepository tradeOrderRepository;
	private final OfferService offerService;
	private final WishlistRepository wishlistRepository;
	private final NotificationService notificationService;
	private final ListingMapper listingMapper;

	/**
	 * 판매 전환.
	 *
	 * 항상 새 행을 만든다 — 미판매로 내린 뒤 다시 눌러도 닫힌 행을 되살리지 않는다.
	 * 그 행을 가리키는 purchase_offer·chat_room 의 조건 스냅샷이 사후에 바뀌기 때문이다(DEC-026).
	 */
	@Transactional
	@Loggable("판매 전환")
	public ListingIdResponse create(Long userId, Long itemId, ListingCreateRequest request) {

		// 현재 소유자만 팔 수 있음
		Item item = findOwnedItem(userId, itemId);
		User seller = currentOwner(itemId);

		// 정산 계좌가 없으면 판매 대금을 받을 곳이 없음
		if (!payoutAccountRepository.existsByUserId(userId)) {
			log.info("정산 계좌 없이 판매 전환을 시도했습니다. userId={}", userId);
			throw new CustomException(PayoutErrorCode.PAYOUT_400_REQUIRED);
		}

		// 이미 판매중인 아이템이면 중복 판매 할 수 없음
		if (itemListingRepository.findOpenByItemId(itemId).isPresent()) {
			log.info("이미 판매 중인 아이템 입니다. itemId={}", itemId);
			throw new CustomException(ListingErrorCode.LISTING_409_ALREADY_OPEN);
		}

		// 폼이 아이템 정보도 다시 받으므로 함께 갱신함
		Category category = applyItemChanges(item, request.getItem());

		// 닫힌 행을 되살리지 않고 늘 새로 만듦
		ItemListing listing = itemListingRepository.save(
				ItemListing.open(
						item, seller,
						request.getPrice(),
						resolveShippingFee(seller, request),
						resolveFeeBearer(seller, request),
						request.getSellerNote(),
						LocalDateTime.now()
				)
		);

		// 실측·상태는 아이템에 붙고 대표 OOTD 는 판매 등록에 붙음
		itemFormApplier.replaceMeasurements(item, category, request.getMeasurements());
		itemFormApplier.replaceConditions(item, request.getConditions());
		replaceFeaturedPosts(listing, userId, request.getFeaturedPostIds());

		// 배지를 그리는 상태는 아이템이 가짐
		item.changeSaleStatus(SaleStatus.ON_SALE);

		// 이 아이템을 미판매 상태일 때 관심 등록해 둔 사람이 있을 수 있음 — 다시 팔린다는 것을 알림
		// 알림 받을 사람이 없으면 notifyAll 내부에서 바로 끝나므로 여기서 미리 거를 필요 없음
		notifyWishlisters(item, "판매 중으로 전환됐어요.");

		return listingMapper.toListingIdResponse(listing);
	}

	/**
	 * 판매 정보 수정. 수정 화면이 판매 전환과 같은 전체 폼이라 요청도 같다.
	 *
	 * 행을 새로 만들지 않고 이 행을 덮어쓴다 — 조건을 바꾸는 것이지 다시 파는 것이 아니다.
	 * 이미 전송된 제안의 금액은 purchase_offer 가 자체 스냅샷으로 들고 있어 바뀌지 않는다.
	 */
	@Transactional
	@Loggable("판매 정보 수정")
	public ListingIdResponse update(Long userId, Long listingId, ListingCreateRequest request) {
		ItemListing listing = findOwnListing(userId, listingId);

		// 판매중이 아니면 수정 판매상태 수정 불가
		if (listing.isClosed()) {
			log.info("판매가 끝난 등록을 수정하려 했다 listingId={}", listingId);
			throw new CustomException(ListingErrorCode.LISTING_409_CLOSED);
		}

		// 판매중인 아이템 조회
		Item item = listing.getItem();

		// 수정 화면도 전체 폼이라 아이템까지 갈아끼움
		Category category = applyItemChanges(item, request.getItem());

		// changeTerms 가 price 를 덮어쓰기 전에 지금 값을 떼어 둠 — 나중에 바뀌었는지 비교할 기준값임
		Integer previousPrice = listing.getPrice();

		// 행을 새로 만들지 않고 조건만 덮어씀
		listing.changeTerms(
				request.getPrice(),
				resolveShippingFee(listing.getSeller(), request),
				resolveFeeBearer(listing.getSeller(), request),
				request.getSellerNote());

		itemFormApplier.replaceMeasurements(item, category, request.getMeasurements());
		itemFormApplier.replaceConditions(item, request.getConditions());
		replaceFeaturedPosts(listing, userId, request.getFeaturedPostIds());

		// 가격만 알림 대상임 — 위에서 떼어 둔 previousPrice 와 지금 요청값을 비교해서 실제로 바뀐 경우만 보냄
		// 배송비·판매자 설명만 바뀐 요청은 여기 안 걸려서 알림이 안 나감
		if (!previousPrice.equals(request.getPrice())) {
			notifyWishlisters(item, String.format("가격이 %,d원으로 변경됐어요.", request.getPrice()));
		}

		return listingMapper.toListingIdResponse(listing);
	}

	/**
	 * 판매 상태 변경. 키가 아이템이다 — 상태는 item 이 갖고, 미판매 아이템에는 가리킬 등록이 없다.
	 *
	 * ON_SALE 은 받지 않는다. 메뉴 모달에 '판매중으로 되돌리기' 가 없고,
	 * 다시 팔려면 판매 전환을 거치며 그때 새 등록이 생긴다.
	 *
	 * 판매중 -> 미판매, 판매중 -> 판매완료
	 */
	@Transactional
	@Loggable("판매 상태 변경")
	public SaleStatusResponse changeSaleStatus(Long userId, Long itemId, SaleStatus saleStatus) {
		// 변경하려는 상태가 ON_SALE은 지원 안함. ON_SALE 변경 판매등록임
		if (saleStatus == SaleStatus.ON_SALE) {
			log.info("판매중으로 되돌리려 했다 itemId={}", itemId);
			throw new CustomException(ListingErrorCode.LISTING_400_STATUS);
		}

		// 아이템을 먼저 잠근다 — OfferService.accept() 도 같은 순서(item → offer)로 잠그므로
		// 두 경로가 서로 다른 순서로 걸어 교착할 일이 없다
		Item item = findOwnedItemForUpdate(userId, itemId);

		// 거래가 진행중이면 상태 변경 불가. 수락 전 PENDING 제안은 거래가 아직 없어 여기 안 걸림
		if (tradeOrderRepository.existsInProgress(itemId, OrderStatus.FINISHED)) {
			log.info("진행 중인 거래가 있어 상태를 바꿀 수 없다 itemId={}", itemId);
			throw new CustomException(ListingErrorCode.LISTING_409_IN_TRADE);
		}

		// 미판매든 판매 완료든 판매는 끝나므로 열린 등록을 함께 닫음
		itemListingRepository.findOpenByItemId(itemId)
				.ifPresent(listing -> listing.close(LocalDateTime.now()));

		// 응답을 못 받은 대기 제안이 남아 있으면 함께 정리함 — 막아두면 결제 승인이 잡힌 채 방치됨
		offerService.cancelPendingForDelist(itemId, OfferCancelReason.LISTING_DELISTED);

		item.changeSaleStatus(saleStatus);

		// 두 상태(NOT_FOR_SALE·SOLD) 중 판매 완료만 알림을 보냄 — 미판매 전환은 화면 근거가 없어 뺌
		if (saleStatus == SaleStatus.SOLD) {
			notifyWishlisters(item, "판매 완료됐어요.");
		}

		return listingMapper.toSaleStatusResponse(item);
	}

	/**
	 * 회원탈퇴 시 판매중 아이템 정리. changeSaleStatus 와 같은 일(아이템 잠금 → 등록 닫기
	 * → 대기 제안 정리 → 미판매 전환)을 여러 아이템에 반복함. 탈퇴 트랜잭션은 본인 확인이
	 * 끝난 상태라 소유자 체크는 안 함.
	 *
	 * 진행중 거래 체크도 안 함 — ON_SALE 이면서 거래가 진행중인 경우는 없음(수락 시 거래
	 * 생성과 SOLD 전환이 같이 일어남). id 만 가볍게 읽고 건마다 재잠금해 확인하므로, 그 사이
	 * 다른 제안이 먼저 수락돼 SOLD 로 바뀐 아이템은 건너뜀
	 */
	@Transactional
	@Loggable("탈퇴에 따른 판매중 아이템 정리")
	public void closeOnSaleItemsForWithdrawal(Long userId) {
		LocalDateTime now = LocalDateTime.now();

		for (Long itemId : itemRepository.findOnSaleItemIdsByCurrentOwner(userId)) {
			itemRepository.findActiveForUpdate(itemId).ifPresent(item -> {
				if (item.getSaleStatus() != SaleStatus.ON_SALE) {
					return;
				}

				itemListingRepository.findOpenByItemId(itemId)
						.ifPresent(listing -> listing.close(now));

				offerService.cancelPendingForDelist(itemId, OfferCancelReason.LISTING_DELISTED);

				item.changeSaleStatus(SaleStatus.NOT_FOR_SALE);
			});
		}
	}

	/**
	 * 폼이 다시 받은 아이템 정보를 아이템 도메인에 넘긴다.
	 *
	 * 세부 카테고리인지·사이즈가 그 대분류의 칩인지는 아이템의 불변식이라 ItemFormApplier 가 정본이다.
	 * 여기서 다시 검사하면 사본이 둘이 되고, 실제로 그렇게 두었다가 null 처리가 서로 반대로
	 * 갈린 적이 있다.
	 */
	private Category applyItemChanges(Item item, ListingItemRequest request) {
		Category category = itemFormApplier.apply(item, ItemForm.builder()
				.categoryId(request.getCategoryId())
				.name(request.getName())
				.brandName(request.getBrandName())
				.sizeLabel(request.getSizeLabel())
				.wearTarget(request.getWearTarget())
				.build());

		// 대표 사진은 상세 수정 시트에 없어 판매 전환에서만 바뀜
		item.changeCoverImage(request.getCoverImageKey());

		return category;
	}

	/**
	 * 대표 OOTD 전체 교체. 보낸 순서가 노출 순서가 된다.
	 *
	 * 본인 게시물만 걸 수 있다 — 남의 OOTD 로 내 상품을 증명할 수 없다.
	 */
	private void replaceFeaturedPosts(ItemListing listing, Long userId, List<Long> postIds) {
		// 중복을 걷어낸 뒤 개수를 셈
		List<Long> distinct = postIds.stream().distinct().toList();

		if (distinct.size() < FEATURED_MIN || distinct.size() > FEATURED_MAX) {
			log.info("대표 OOTD 개수가 범위를 벗어났다 count={}", distinct.size());
			throw new CustomException(ListingErrorCode.LISTING_400_FEATURED_RANGE);
		}

		// 한 번에 읽고 보낸 순서대로 다시 세움
		Map<Long, Post> found = postRepository.findActiveByIds(distinct).stream()
				.collect(Collectors.toMap(Post::getId, Function.identity()));

		List<Post> ordered = new ArrayList<>(distinct.size());
		for (Long postId : distinct) {
			Post post = found.get(postId);
			if (post == null) {
				log.info("없거나 삭제된 게시물을 대표 OOTD 로 걸려 했다 postId={}", postId);
				throw new CustomException(PostErrorCode.POST_404);
			}
			if (!post.isAuthor(userId)) {
				log.info("남의 게시물을 대표 OOTD 로 걸려 했다 postId={}", postId);
				throw new CustomException(PostErrorCode.POST_403);
			}
			ordered.add(post);
		}

		// 전체 교체라 비우고 다시 넣음
		listingFeaturedPostRepository.deleteAllByListingId(listing.getId());

		// 보낸 순서가 그대로 노출 순서가 됨
		List<ListingFeaturedPost> rows = new ArrayList<>(ordered.size());
		for (int i = 0; i < ordered.size(); i++) {
			rows.add(ListingFeaturedPost.of(listing, ordered.get(i), i));
		}
		listingFeaturedPostRepository.saveAll(rows);
	}

	/** 기본 사용자는 배송비를 보낼 수 없다. 화면에서도 '0원' 으로 잠겨 있다 */
	private Integer resolveShippingFee(User seller, ListingCreateRequest request) {
		validateFeeFieldsForBasicGrade(seller, request);

		// 기본 사용자는 화면에서 입력란이 잠겨 있음
		if (seller.getGrade().isBasic()) {
			return 0;
		}
		return request.getShippingFee() == null ? 0 : request.getShippingFee();
	}

	/** 기본 사용자는 수수료 부담이 판매자 고정이다 */
	private FeeBearer resolveFeeBearer(User seller, ListingCreateRequest request) {
		validateFeeFieldsForBasicGrade(seller, request);

		// 기본 사용자는 칩이 판매자 부담으로 고정됨
		if (seller.getGrade().isBasic()) {
			return FeeBearer.SELLER;
		}
		return request.getFeeBearer() == null ? FeeBearer.SELLER : request.getFeeBearer();
	}

	/** 기본 사용자는 배송비·수수료 부담을 보낼 수 없다 — 화면 자체가 잠겨 있다 */
	private void validateFeeFieldsForBasicGrade(User seller, ListingCreateRequest request) {
		if (!seller.getGrade().isBasic()) {
			return;
		}
		if (request.getShippingFee() != null || request.getFeeBearer() != null) {
			log.info("기본 사용자가 수수료 부담·배송비를 지정하려 했다 userId={}", seller.getId());
			throw new CustomException(ListingErrorCode.LISTING_403_FEE_BEARER);
		}
	}

	/**
	 * 판매 등록을 찾고 본인 것인지 확인한다.
	 *
	 * 소유자가 아니라 판매자로 판정한다 — 거래가 끝나면 아이템 소유자는 구매자로 바뀌지만
	 * 그 판매 등록을 만든 사람은 여전히 판매자다.
	 */
	private ItemListing findOwnListing(Long userId, Long listingId) {
		ItemListing listing = itemListingRepository.findById(listingId)
				.orElseThrow(() -> {
					log.info("없는 판매 등록이다 listingId={}", listingId);
					return new CustomException(ListingErrorCode.LISTING_404);
				});

		// 소유자가 아니라 판매자로 봄. 거래 뒤에는 소유자가 구매자로 바뀜
		if (!listing.isSeller(userId)) {
			log.info("남의 판매 등록을 건드리려 했다 listingId={} userId={}", listingId, userId);
			throw new CustomException(ListingErrorCode.LISTING_403);
		}
		return listing;
	}

	/** 현재 소유자만 팔 수 있다 — 등록자여도 이미 팔았으면 남의 옷이다. */
	private Item findOwnedItem(Long userId, Long itemId) {
		Item item = itemRepository.findActiveWithCategory(itemId)
				.orElseThrow(() -> {
					log.info("없거나 삭제된 아이템이다 itemId={}", itemId);
					return new CustomException(ItemErrorCode.ITEM_404);
				});

		// 등록자여도 이미 팔았으면 남의 옷임
		if (!currentOwner(itemId).getId().equals(userId)) {
			log.info("현재 소유자가 아닌 사람이 판매를 시도했다 itemId={} userId={}", itemId, userId);
			throw new CustomException(ItemErrorCode.ITEM_403);
		}
		return item;
	}

	/**
	 * 판매 상태 변경 전용 — 행 잠금 조회임. 이 안에서 item_listing·purchase_offer 를 함께
	 * 건드리므로, 동시에 들어오는 제안 수락과 아이템 행을 두고 경합하지 않게 먼저 잠근다
	 */
	private Item findOwnedItemForUpdate(Long userId, Long itemId) {
		Item item = itemRepository.findActiveForUpdate(itemId)
				.orElseThrow(() -> {
					log.info("없거나 삭제된 아이템이다 itemId={}", itemId);
					return new CustomException(ItemErrorCode.ITEM_404);
				});

		if (!currentOwner(itemId).getId().equals(userId)) {
			log.info("현재 소유자가 아닌 사람이 판매를 시도했다 itemId={} userId={}", itemId, userId);
			throw new CustomException(ItemErrorCode.ITEM_403);
		}
		return item;
	}

	/**
	 * 가격 변동·거래 상태를 관심 등록한 사람에게 알림. 벨을 꺼둔 사람은 WishlistRepository 가 이미 걸러줌.
	 * 수신자 수만큼 설정 조회가 나가지 않게 notifyAll 로 한 번에 발행함
	 */
	private void notifyWishlisters(Item item, String body) {
		// 이 아이템을 담고 알림(벨)도 켜 둔 사용자만 받아옴 — 담기만 하고 벨을 끈 사람은 쿼리에서 이미 빠짐
		// 받아온 목록을 notificationService.notifyAll 에 그대로 넘겨 설정 조회·저장을 한 번씩만 하게 함
		notificationService.notifyAll(
				wishlistRepository.findAlarmEnabledUsersByItemId(item.getId()),
				NotificationSettingType.WISHLIST_ITEM, NotificationCategory.WISHLIST,
				item.getName(), body, item.getCoverImageKey(), NotificationTargetType.ITEM, item.getId());
	}

	private User currentOwner(Long itemId) {
		return itemOwnershipRepository.findCurrent(itemId)
				.map(ItemOwnership::getOwner)
				.orElseThrow(() -> {
					log.info("본인의 아이템이 아닙니다. itemId={}", itemId);
					return new CustomException(ItemErrorCode.ITEM_403);
				});
	}


}
