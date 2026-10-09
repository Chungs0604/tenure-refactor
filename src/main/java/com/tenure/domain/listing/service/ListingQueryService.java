package com.tenure.domain.listing.service;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemImage;
import com.tenure.domain.item.entity.ItemMeasurement;
import com.tenure.domain.item.enums.ConditionCode;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.repository.ItemConditionRepository;
import com.tenure.domain.item.repository.ItemImageRepository;
import com.tenure.domain.item.repository.ItemMeasurementRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.item.repository.ItemWearRecordRepository;
import com.tenure.domain.listing.dto.response.FeaturedPostResponse;
import com.tenure.domain.listing.dto.response.ItemDetailResponse;
import com.tenure.domain.listing.dto.response.ListingSellerResponse;
import com.tenure.domain.listing.dto.response.ListingTermsResponse;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.listing.entity.ListingFeaturedPost;
import com.tenure.domain.listing.mapper.ListingMapper;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.listing.repository.ListingFeaturedPostRepository;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.repository.PostImageRepository;
import com.tenure.domain.post.repository.PostItemTagRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.mapper.UserMapper;
import com.tenure.domain.wishlist.repository.WishlistRepository;
import com.tenure.domain.offer.repository.PurchaseOfferRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import com.tenure.global.storage.FileStorage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이템 상세(공개 페이지)와 대표 OOTD 조회.
 *
 * 조회 키가 itemId 다 — 판매중·미판매·판매완료가 한 화면의 상태 변형이고, 판매 전환을 한 번도
 * 안 한 아이템에는 item_listing 행이 아예 없어 listingId 로는 열 수 없다 (DEC-026).
 *
 * 상태별로 그리는 것이 다르다.
 *   ON_SALE                열린 등록의 조건 + 판매자 정보 + 대표 OOTD
 *   NOT_FOR_SALE(이력 있음) 조건 없음 + 대표 OOTD
 *   NOT_FOR_SALE(이력 없음) 조건 없음 + 태그된 OOTD
 *   SOLD                   조건 없음 + 판매자 정보 + 있는 쪽 OOTD
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingQueryService {

	/** 상세의 가로 스크롤이 보여주는 장수. 더보기는 전체 목록으로 간다 */
	private static final int PREVIEW_SIZE = 10;

	private final ItemRepository itemRepository;
	private final ItemImageRepository itemImageRepository;
	private final ItemMeasurementRepository itemMeasurementRepository;
	private final ItemConditionRepository itemConditionRepository;
	private final ItemWearRecordRepository itemWearRecordRepository;
	private final ItemListingRepository itemListingRepository;
	private final ListingFeaturedPostRepository listingFeaturedPostRepository;
	private final PostItemTagRepository postItemTagRepository;
	private final WishlistRepository wishlistRepository;
	private final PurchaseOfferRepository purchaseOfferRepository;
	private final PostImageRepository postImageRepository;
	private final ListingMapper listingMapper;
	private final FileStorage fileStorage;

	/** 아이템 상세 (공개 페이지). */
	@Loggable("아이템 상세 조회")
	public ItemDetailResponse getItemDetail(Long userId, Long itemId) {
		// 카테고리는 스펙 그리드가 그림
		Item item = itemRepository.findActiveWithCategory(itemId)
				.orElseThrow(() -> {
					log.info("없거나 삭제된 아이템이다 itemId={}", itemId);
					return new CustomException(ItemErrorCode.ITEM_404);
				});

		// 최신 등록 한 건만 읽고 열림 여부를 가림. 열린 행은 늘 최신이라 이렇게 파생된다
		Optional<ItemListing> latestListing = itemListingRepository.findLatestByItemId(itemId);

		// 판매를 내리면 조건 섹션이 통째로 접히므로 열린 등록일 때만 조건을 싣는다
		Optional<ItemListing> openListing = latestListing.filter(ItemListing::isOpen);

		// 판매 이력이 있으면 대표 OOTD, 없으면 태그된 OOTD. 둘은 배타적임
		List<FeaturedPostResponse> featuredPosts = List.of();
		List<FeaturedPostResponse> taggedPosts = List.of();

		if (latestListing.isPresent()) {
			featuredPosts = featuredPostsOf(latestListing.get().getId());
		} else {
			taggedPosts = taggedPostsOf(itemId);
		}

		return listingMapper.toItemDetailResponse(
				item,
				imageUrlsOf(itemId),
				wishlistRepository.existsByUserIdAndItemId(userId, itemId),
				totalWearCountOf(itemId),
				openListing.map(this::toTerms).orElse(null),
				latestListing.map(ItemListing::getSeller).map(this::toSeller).orElse(null),
				featuredPosts,
				taggedPosts,
				purchaseOfferRepository.findLatestStatus(itemId, userId).orElse(null));
	}	

	/**
	 * 대표 OOTD 전체 보기. 열린 등록이 없으면 가장 최근 등록의 것을 보여준다 —
	 * 미판매로 내려도 대표 OOTD 는 화면에 남는다(2825:60351).
	 */
	@Loggable("대표 OOTD 조회")
	public List<FeaturedPostResponse> getFeaturedPosts(Long itemId) {
		// 없는 아이템을 빈 목록으로 돌려주면 404 와 구분되지 않음
		if (itemRepository.findActive(itemId).isEmpty()) {
			log.info("없거나 삭제된 아이템의 대표 OOTD 를 요청했다 itemId={}", itemId);
			throw new CustomException(ItemErrorCode.ITEM_404);
		}

		// 미판매로 내려도 대표 OOTD 는 남으므로 열림 여부를 가리지 않고 최신 등록을 봄
		return itemListingRepository.findLatestByItemId(itemId)
				.map(listing -> featuredPostsOf(listing.getId()))
				.orElseGet(List::of);
	}

	private ListingTermsResponse toTerms(ItemListing listing) {
		Long itemId = listing.getItem().getId();

		// 실측·상태는 판매 등록이 아니라 아이템에 붙음
		List<ConditionCode> conditions = itemConditionRepository.findAllByItemId(itemId).stream()
				.map(condition -> condition.getConditionCode())
				.toList();

		// 채운 실측 항목만 담겨 응답에 null 이 끼지 않음
		return listingMapper.toListingTermsResponse(
				listing,
				String.valueOf(listing.getPrice()),
				String.valueOf(listing.getShippingFee()),
				// 세트는 대분류가 가짐. 상세 조회가 카테고리를 부모까지 함께 읽어 추가 쿼리가 없음
				listing.getItem().getCategory().resolveMeasurementScheme(),
				itemMeasurementRepository.findByItemId(itemId)
						.map(ItemMeasurement::toValues)
						.orElseGet(Map::of),
				conditions);
	}

	private ListingSellerResponse toSeller(User seller) {
		// 탈퇴한 판매자는 닉네임을 가림
		return listingMapper.toListingSellerResponse(
				seller,
				seller.isWithdrawn() ? UserMapper.WITHDRAWN_NICKNAME : seller.getNickname(),
				seller.getProfileImageKey() == null
						? null
						: fileStorage.toUrl(seller.getProfileImageKey()));
	}

	private List<FeaturedPostResponse> featuredPostsOf(Long listingId) {

		// 선택 순서대로 내려옴
		List<Post> posts = listingFeaturedPostRepository.findAllByListingId(listingId).stream()
				.map(ListingFeaturedPost::getPost)
				.toList();
		return toPostCards(posts);
	}

	private List<FeaturedPostResponse> taggedPostsOf(Long itemId) {
		return toPostCards(
				postItemTagRepository.findTaggedPosts(itemId, Pageable.ofSize(PREVIEW_SIZE)));
	}


	/** 카드마다 대표 사진을 다시 읽으면 장수만큼 쿼리가 늘어남 */
	private List<FeaturedPostResponse> toPostCards(List<Post> posts) {
		if (posts.isEmpty()) {
			return List.of();
		}

		Map<Long, String> covers = postImageRepository
				.findCovers(posts.stream().map(Post::getId).toList()).stream()
				.collect(Collectors.toMap(
						image -> image.getPost().getId(),
						image -> fileStorage.toUrl(image.getImageKey())));

		return posts.stream()
				.map(post -> listingMapper.toFeaturedPostResponse(post, covers.get(post.getId())))
				.toList();
	}

	private List<String> imageUrlsOf(Long itemId) {
		List<ItemImage> images = itemImageRepository.findAllByItemId(itemId);
		return images.stream()
				.map(image -> fileStorage.toUrl(image.getImageKey()))
				.toList();
	}

	/** 'OOTD 인증 N회'. 소유 구간별 합계가 아이템 전체 인증 횟수다. */
	private Integer totalWearCountOf(Long itemId) {

		// 소유자가 바뀌어도 기록은 이어지므로 구간 합계가 전체 인증 횟수임
		return itemWearRecordRepository.countByOwnership(itemId).stream()
				.mapToInt(row -> row.wearCount().intValue())
				.sum();
	}
}
