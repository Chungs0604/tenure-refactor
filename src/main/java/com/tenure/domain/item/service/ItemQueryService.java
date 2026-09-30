package com.tenure.domain.item.service;

import com.tenure.domain.item.dto.response.ItemHistoryPostResponse;
import com.tenure.domain.item.dto.response.ItemListingSummaryResponse;
import com.tenure.domain.item.dto.response.ItemOwnershipHistoryResponse;
import com.tenure.domain.item.dto.response.ItemOwnershipResponse;
import com.tenure.domain.item.dto.response.ItemRecommendationResponse;
import com.tenure.domain.item.dto.response.ItemResponse;
import com.tenure.domain.item.dto.response.ItemSummaryResponse;
import com.tenure.domain.item.dto.response.ItemWornWithResponse;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.RecommendationBlock;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.mapper.ItemMapper;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.item.repository.ItemWearRecordRepository;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.listing.enums.SaleStatus;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.post.dto.response.PostSummaryResponse;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.enums.AspectRatio;
import com.tenure.domain.post.mapper.PostMapper;
import com.tenure.domain.post.repository.PostImageRepository;
import com.tenure.domain.post.repository.PostItemTagRepository;
import com.tenure.domain.post.repository.PostLikeRepository;
import com.tenure.domain.post.repository.PostSaveRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.mapper.UserMapper;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.storage.FileStorage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이템 조회 — 내 옷장 목록, 상세, 히스토리, Tenure 기록 기준, 태그 가능 목록.
 *
 * 목록은 현재 소유자 기준이다. 아이템을 팔면 목록에서 빠지고 상세도 열리지 않는다 — 등록자
 * 기준으로 두면 남의 옷을 내가 수정할 수 있게 된다.
 *
 * 최근 착용일과 판매 등록은 페이지의 id 목록으로 한 번에 읽어 맞춘다. 행마다 조회하면
 * 카드 수만큼 쿼리가 늘어난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemQueryService {

	/** '자주 같이 입은 옷' 칩 개수. 화면이 3개를 그린다 */
	private static final int WORN_WITH_LIMIT = 3;

	/** 추천 블록마다 내려주는 게시물 수 */
	private static final int BLOCK_SIZE = 10;

	private final ItemRepository itemRepository;
	private final ItemOwnershipRepository itemOwnershipRepository;
	private final ItemWearRecordRepository itemWearRecordRepository;
	private final ItemListingRepository itemListingRepository;
	private final PostItemTagRepository postItemTagRepository;
	private final PostImageRepository postImageRepository;
	private final PostLikeRepository postLikeRepository;
	private final PostSaveRepository postSaveRepository;
	private final PostMapper postMapper;
	private final ItemMapper itemMapper;
	private final FileStorage fileStorage;

	/** 내 옷장. 검색어는 브랜드명·아이템명 부분 일치다. */
	@Loggable("내 아이템 목록 조회")
	public CursorSlice<ItemSummaryResponse> getMyItems(
			Long userId, String keyword, Cursor cursor, int size
	) {
		List<Item> items = itemRepository.findOwnedItems(userId, keyword, cursor, size);

		if (items.isEmpty()) {
			return CursorSlice.empty();
		}

		CursorSlice<Item> slice = CursorSlice.of(items, size, it -> Cursor.of(it.getCreatedAt(), it.getId()));

		// 행마다 조회하면 카드 수만큼 쿼리가 늘어남
		List<Long> itemIds = slice.getContent().stream().map(Item::getId).toList();

		// 아이템 별 최근 착용 일
		Map<Long, LocalDate> lastWornDates = lastWornDatesOf(itemIds);

		// 아이템 별 최신 판매 등록
		Map<Long, ItemListing> listings = latestListingsOf(itemIds);

		return slice.map(
				item -> toSummary(item, lastWornDates.get(item.getId()), listings.get(item.getId()))
		);
	}

	/**
	 * 태그 가능한 내 아이템. 현재 소유 구간이 열려 있는 것만 내려간다 — 목록과 조건이 같다.
	 *
	 * 행마다 '최근 착용 7일 전 · OOTD 인증 : 2회' 를 그린다. 인증 횟수는 전체 누적이 아니라
	 * 현재 소유 구간 기준이다 — 내 옷장 맥락이라 '내가 이 옷을 얼마나 입었나' 를 보여준다.
	 *
	 * 판매 상태는 그리지 않으므로 판매 등록을 읽지 않는다.
	 */
	@Loggable("태그 가능한 아이템 조회")
	public CursorSlice<ItemSummaryResponse> getTaggableItems(
			Long userId, String keyword, Cursor cursor, int size
	) {
		List<Item> items = itemRepository.findOwnedItems(userId, keyword, cursor, size);
		if (items.isEmpty()) {
			return CursorSlice.empty();
		}

		CursorSlice<Item> slice = CursorSlice.of(items, size, it -> Cursor.of(it.getCreatedAt(), it.getId()));

		// 행마다 조회하면 아이템 수만큼 쿼리가 늘어남
		List<Long> itemIds = slice.getContent().stream().map(Item::getId).toList();
		Map<Long, LocalDate> lastWornDates = lastWornDatesOf(itemIds);
		Map<Long, Integer> wearCounts = currentWearCountsOf(itemIds);

		return slice.map(item -> toTaggableSummary(
				item,
				lastWornDates.get(item.getId()),
				wearCounts.getOrDefault(item.getId(), 0)));
	}

	/** 아이템 상세 (소유자 시점). */
	@Loggable("아이템 상세 조회")
	public ItemResponse getItem(Long userId, Long itemId) {
		Item item = findOwnedItem(userId, itemId);

		ItemOwnershipHistoryResponse ownerships = buildOwnershipHistory(item);
		ItemListing listing = latestListingsOf(List.of(itemId)).get(itemId);

		return itemMapper.toItemResponse(
				item,
				fileStorage.toUrl(item.getCoverImageKey()),
				saleStatusOf(listing),
				ownerships.getOwnerships(),
				findFrequentlyWornWith(itemId),
				ownerships.getRecentWornDate(),
				ownerships.getTotalWearCount(),
				toListingSummary(listing));
	}

	/**
	 * Tenure 기록 기준. 소유자가 아니어도 볼 수 있다 — 구매자 시점의 상품 상세도 같은 섹션을 쓴다.
	 */
	@Loggable("소유 구간 조회")
	public ItemOwnershipHistoryResponse getOwnerships(Long itemId) {
		Item item = itemRepository.findActive(itemId)
				.orElseThrow(() -> new CustomException(ItemErrorCode.ITEM_404));

		return buildOwnershipHistory(item);
	}

	/**
	 * 아이템 히스토리. 소유 구간 카드의 '더보기' 로 들어오지만 아이템 전체를 보여준다 —
	 * 화면 제목이 '아이템 히스토리' 고 그리드에 구간 구분이 없다.
	 */
	@Loggable("아이템 히스토리 조회")
	public CursorSlice<ItemHistoryPostResponse> getHistory(Long itemId, Cursor cursor, int size) {
		// 없는 아이템의 히스토리를 빈 목록으로 돌려주면 404 와 구분되지 않음
		if (!itemRepository.findActive(itemId).isPresent()) {
			log.info("없거나 삭제된 아이템의 히스토리를 요청했다 itemId={}", itemId);
			throw new CustomException(ItemErrorCode.ITEM_404);
		}

		// row = {postId, 대표 사진 키, 촬영 비율, 착용일}
		List<ItemHistoryPostResponse> rows = itemWearRecordRepository.findHistory(itemId, cursor, size)
				.stream()
				.map(row -> itemMapper.toItemHistoryPostResponse(
						(Long) row[0],
						fileStorage.toUrl((String) row[1]),
						(AspectRatio) row[2],
						(LocalDate) row[3]))
				.toList();

		return CursorSlice
				.of(rows, size, it -> Cursor.of(it.getWornDate(), it.getPostId()));
	}

	/**
	 * 아이템 기준 추천. 블록 셋을 각각 최대 10건씩 모은다.
	 *
	 * 비어 있는 블록은 내려보내지 않는다 — 화면이 제목만 있고 카드가 없는 줄을 그리지 않는다.
	 */
	@Loggable("아이템 추천 조회")
	public List<ItemRecommendationResponse> getRecommendations(Long userId, Long itemId) {
		Item item = itemRepository.findActiveWithCategory(itemId)
				.orElseThrow(() -> new CustomException(ItemErrorCode.ITEM_404));

		List<ItemRecommendationResponse> blocks = new ArrayList<>();

		for (RecommendationBlock block : RecommendationBlock.values()) {
			List<Post> posts = itemRepository.findRecommendedPosts(userId, item, block, BLOCK_SIZE);

			// 제목만 있고 카드가 없는 줄은 그리지 않음
			if (posts.isEmpty()) {
				continue;
			}

			blocks.add(itemMapper.toItemRecommendationResponse(
					block,
					block.titleOf(item.getBrandName(), item.getName()),
					block.descriptionOf(item.getCategory().getName()),
					toCards(userId, posts)));
		}

		return blocks;
	}

	/**
	 * 피드와 같은 카드로 만든다. 대표 사진·좋아요·저장 여부를 게시물마다 조회하면 카드 수만큼
	 * 쿼리가 늘어나므로 id 목록으로 한 번에 읽어 맞춘다.
	 */
	private List<PostSummaryResponse> toCards(Long userId, List<Post> posts) {
		List<Long> postIds = posts.stream().map(Post::getId).toList();

		// 대표 사진·좋아요·저장 여부를 한 번씩만 읽어 맞춤
		// Map<PostId, imageKey>
		Map<Long, String> covers = postImageRepository.findCovers(postIds).stream()
				.collect(Collectors.toMap(
						image -> image.getPost().getId(),
						image -> fileStorage.toUrl(image.getImageKey())));

		// 좋아요, 저장 누른 PostId
		Set<Long> liked = new HashSet<>(postLikeRepository.findLikedPostIds(userId, postIds));
		Set<Long> saved = new HashSet<>(postSaveRepository.findSavedPostIds(userId, postIds));

		return posts.stream()
				.map(post -> postMapper.toPostSummaryResponse(
						post,
						covers.get(post.getId()),
						liked.contains(post.getId()),
						saved.contains(post.getId())))
				.toList();
	}

	/**
	 * 소유 구간 카드 조립. 인증 횟수와 미리보기 사진을 한 번씩만 읽어 구간에 맞춘다.
	 */
	private ItemOwnershipHistoryResponse buildOwnershipHistory(Item item) {
		List<ItemOwnership> ownerships = itemOwnershipRepository.findAllByItemId(item.getId());

		Map<Long, Integer> wearCounts = itemWearRecordRepository.countByOwnership(item.getId()).stream()
				.collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));

		// 구간별 최신 3장을 한 쿼리로 받아 구간에 맞춤
		Map<Long, List<String>> previews = new HashMap<>();
		for (Object[] row : itemWearRecordRepository.findOwnershipPreviews(item.getId())) {
			previews.computeIfAbsent((Long) row[0], key -> new ArrayList<>())
					.add(fileStorage.toUrl((String) row[1]));
		}

		List<ItemOwnershipResponse> cards = ownerships.stream()
				.map(ownership -> itemMapper.toItemOwnershipResponse(
						ownership,
						nicknameOf(ownership.getOwner()),
						profileImageUrlOf(ownership.getOwner()),
						wearCounts.getOrDefault(ownership.getId(), 0),
						previews.getOrDefault(ownership.getId(), List.of())))
				.toList();

		int totalWearCount = wearCounts.values().stream().mapToInt(Integer::intValue).sum();

		return itemMapper.toItemOwnershipHistoryResponse(
				cards,
				totalWearCount,
				item.getFirstOwnedDate(),
				lastWornDatesOf(List.of(item.getId())).get(item.getId()));
	}

	private List<ItemWornWithResponse> findFrequentlyWornWith(Long itemId) {
		List<Object[]> rows = postItemTagRepository.findFrequentlyWornWith(
				itemId, PageRequest.of(0, WORN_WITH_LIMIT));

		if (rows.isEmpty()) {
			return List.of();
		}

		// 칩에 브랜드·아이템명을 그려야 해서 id 로 한 번 더 읽음
		List<Long> itemIds = rows.stream().map(row -> (Long) row[0]).toList();
		Map<Long, Item> items = itemRepository.findAllByIds(itemIds).stream()
				.collect(Collectors.toMap(Item::getId, Function.identity()));

		// 겹친 횟수 순서를 잃지 않도록 조회 결과 순서대로 다시 만든다
		return itemIds.stream()
				.map(items::get)
				.filter(java.util.Objects::nonNull)
				.map(itemMapper::toItemWornWithResponse)
				.toList();
	}

	/**
	 * 소유자 본인의 아이템만 연다. 판 아이템을 열면 403 이다 — 그 화면에는 수정·삭제 진입이 있다.
	 */
	private Item findOwnedItem(Long userId, Long itemId) {
		Item item = itemRepository.findActiveWithCategory(itemId)
				.orElseThrow(() -> new CustomException(ItemErrorCode.ITEM_404));

		itemOwnershipRepository.findCurrent(itemId)
				.filter(ownership -> ownership.getOwner().getId().equals(userId))
				.orElseThrow(() -> {
					log.info("현재 소유자가 아니다 userId={} itemId={}", userId, itemId);
					return new CustomException(ItemErrorCode.ITEM_403);
				});

		return item;
	}

	/**
	 * 아이템 별 최근 작용 일
	 * Map<ItemId, LastWornAt)
	 */
	/** 현재 소유 구간의 인증 횟수. 태그 시트의 'OOTD 인증 : N회' 다. */
	private Map<Long, Integer> currentWearCountsOf(Collection<Long> itemIds) {
		return itemWearRecordRepository.countCurrentOwnershipWears(itemIds).stream()
				.collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));
	}

	private Map<Long, LocalDate> lastWornDatesOf(Collection<Long> itemIds) {
		return itemWearRecordRepository.findLastWornDates(itemIds).stream()
				.collect(Collectors.toMap(row -> (Long) row[0], row -> (LocalDate) row[1]));
	}

	/**
	 * 아이템 별 최신 판매 등록
	 * Map<ItemId, ItemListing(판매등록)>
	 */
	private Map<Long, ItemListing> latestListingsOf(Collection<Long> itemIds) {
		return itemListingRepository.findLatestByItemIds(itemIds).stream()
				.collect(Collectors.toMap(listing -> listing.getItem().getId(), Function.identity()));
	}

	/** 태그 시트 행. 판매 상태 자리는 비우고 인증 횟수를 채운다. */
	private ItemSummaryResponse toTaggableSummary(Item item, LocalDate lastWornDate, int wearCount) {
		return itemMapper.toItemSummaryResponse(
				item, fileStorage.toUrl(item.getCoverImageKey()), null, lastWornDate, wearCount, null);
	}

	/** 내 옷장 행. 인증 횟수 자리는 비우고 판매 상태를 채운다. */
	private ItemSummaryResponse toSummary(Item item, LocalDate lastWornDate, ItemListing listing) {
		return itemMapper.toItemSummaryResponse(
				item, fileStorage.toUrl(item.getCoverImageKey()), saleStatusOf(listing),
				lastWornDate, null, toListingSummary(listing));
	}

	/** 판매 등록이 없는 것과 판매를 내린 것을 화면은 똑같이 '미판매' 로 그린다. */
	private SaleStatus saleStatusOf(ItemListing listing) {
		return listing == null ? SaleStatus.NOT_FOR_SALE : listing.getSaleStatus();
	}

	private ItemListingSummaryResponse toListingSummary(ItemListing listing) {
		return itemMapper.toListingSummaryResponse(listing);
	}

	private String nicknameOf(User owner) {
		return owner.isWithdrawn() ? UserMapper.WITHDRAWN_NICKNAME : owner.getNickname();
	}

	private String profileImageUrlOf(User owner) {
		return owner.getProfileImageKey() == null ? null : fileStorage.toUrl(owner.getProfileImageKey());
	}
}
