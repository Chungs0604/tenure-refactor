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
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.order.repository.TradeOrderRepository;
import com.tenure.domain.payout.exception.PayoutErrorCode;
import com.tenure.domain.payout.repository.PayoutAccountRepository;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.exception.PostErrorCode;
import com.tenure.domain.post.repository.PostRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

	/** 끝난 거래. 이 둘만 아니면 진행 중으로 본다 */
	private static final Set<OrderStatus> FINISHED_ORDER_STATUSES =
			EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELED);

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

		// 행을 새로 만들지 않고 조건만 덮어씀
		listing.changeTerms(
				request.getPrice(),
				resolveShippingFee(listing.getSeller(), request),
				resolveFeeBearer(listing.getSeller(), request),
				request.getSellerNote());

		itemFormApplier.replaceMeasurements(item, category, request.getMeasurements());
		itemFormApplier.replaceConditions(item, request.getConditions());
		replaceFeaturedPosts(listing, userId, request.getFeaturedPostIds());

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

		Item item = findOwnedItem(userId, itemId);

		// 거래가 진행중이면 상태 변경 불가.
		// 수락 전 PENDING 제안은 거래가 아직 없어 여기 안 걸림 — 그 결제 승인 해제는
		// offer·payment 를 만들 때 함께 처리한다 (Q8)
		if (tradeOrderRepository.existsInProgress(itemId, FINISHED_ORDER_STATUSES)) {
			log.info("진행 중인 거래가 있어 상태를 바꿀 수 없다 itemId={}", itemId);
			throw new CustomException(ListingErrorCode.LISTING_409_IN_TRADE);
		}

		// 미판매든 판매 완료든 판매는 끝나므로 열린 등록을 함께 닫음
		itemListingRepository.findOpenByItemId(itemId)
				.ifPresent(listing -> listing.close(LocalDateTime.now()));

		item.changeSaleStatus(saleStatus);

		return listingMapper.toSaleStatusResponse(item);
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
	private List<Post> replaceFeaturedPosts(ItemListing listing, Long userId, List<Long> postIds) {
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

		return ordered;
	}

	/** 기본 사용자는 배송비를 보낼 수 없다. 화면에서도 '0원' 으로 잠겨 있다 */
	private Integer resolveShippingFee(User seller, ListingCreateRequest request) {

		// 기본 사용자는 화면에서 입력란이 잠겨 있음
		if (seller.getGrade().isBasic()) {
			requireNotSet(request.getShippingFee(), request.getFeeBearer(), seller);
			return 0;
		}
		return request.getShippingFee() == null ? 0 : request.getShippingFee();
	}

	/** 기본 사용자는 수수료 부담이 판매자 고정이다 */
	private FeeBearer resolveFeeBearer(User seller, ListingCreateRequest request) {

		// 기본 사용자는 칩이 판매자 부담으로 고정됨
		if (seller.getGrade().isBasic()) {
			requireNotSet(request.getShippingFee(), request.getFeeBearer(), seller);
			return FeeBearer.SELLER;
		}
		return request.getFeeBearer() == null ? FeeBearer.SELLER : request.getFeeBearer();
	}

	private void requireNotSet(Integer shippingFee, FeeBearer feeBearer, User seller) {
		if (shippingFee != null || feeBearer != null) {
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

	private User currentOwner(Long itemId) {
		return itemOwnershipRepository.findCurrent(itemId)
				.map(ItemOwnership::getOwner)
				.orElseThrow(() -> {
					log.info("본인의 아이템이 아닙니다. itemId={}", itemId);
					return new CustomException(ItemErrorCode.ITEM_403);
				});
	}


}
