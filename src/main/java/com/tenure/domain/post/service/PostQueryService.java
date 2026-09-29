package com.tenure.domain.post.service;

import com.tenure.domain.block.repository.UserBlockRepository;
import com.tenure.domain.follow.repository.FollowRepository;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.post.dto.response.MyPostSummaryResponse;
import com.tenure.domain.post.dto.response.PostImageResponse;
import com.tenure.domain.post.dto.response.PostResponse;
import com.tenure.domain.post.dto.response.PostSummaryResponse;
import com.tenure.domain.post.dto.response.PostTagResponse;
import com.tenure.domain.post.dto.response.TaggedItemResponse;
import com.tenure.domain.post.dto.response.UserPostSummaryResponse;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.entity.PostImage;
import com.tenure.domain.post.entity.PostItemTag;
import com.tenure.domain.post.enums.FeedTab;
import com.tenure.domain.post.enums.MyPostTab;
import com.tenure.domain.post.enums.TaggedItemStatus;
import com.tenure.domain.post.exception.PostErrorCode;
import com.tenure.domain.post.mapper.PostMapper;
import com.tenure.domain.post.repository.PostImageRepository;
import com.tenure.domain.post.repository.PostItemTagRepository;
import com.tenure.domain.post.repository.PostLikeRepository;
import com.tenure.domain.post.repository.PostRepository;
import com.tenure.domain.post.repository.PostSaveRepository;
import com.tenure.domain.search.enums.RecentViewTargetType;
import com.tenure.domain.search.store.RecentViewStore;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.domain.wishlist.repository.WishlistRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.storage.FileStorage;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시물 조회. 피드·상세·태그된 아이템·마이페이지·타인 프로필을 담는다.
 *
 * 쓰기와 나눈 이유는 조립 방식이 다르기 때문이다 — 목록은 페이지의 id 를 모아 대표 사진·좋아요
 * 여부·판매 상태를 한 번에 가져와 맞춘다. 카드마다 조회하면 카드 수만큼 쿼리가 늘어난다.
 *
 * 목록은 커서 방식이다. 화면이 전부 무한 스크롤이고 총 건수를 쓰는 곳이 없어 offset·count 가
 * 필요 없으며, 스크롤 중에 새 게시물이 앞에 들어와도 중복·누락이 생기지 않는다.
 *
 * 커서를 값으로 주고받는다. base64 문자열로 바꾸는 일은 전송 형식이라 Controller 가
 * CursorCodec 으로 처리한다 — 여기서는 정렬 키가 무엇인지만 안다.
 *
 * 저장소 키를 URL 로 바꾸는 일은 여기서 한다 — 매퍼가 FileStorage 를 알면 단위 테스트가
 * 저장소 설정에 묶인다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostQueryService {

	private final PostRepository postRepository;
	private final PostImageRepository postImageRepository;
	private final PostItemTagRepository postItemTagRepository;
	private final PostLikeRepository postLikeRepository;
	private final PostSaveRepository postSaveRepository;
	private final ItemListingRepository itemListingRepository;
	private final WishlistRepository wishlistRepository;
	private final FollowRepository followRepository;
	private final UserBlockRepository userBlockRepository;
	private final UserRepository userRepository;
	private final PostMapper postMapper;
	private final FileStorage fileStorage;
	private final RecentViewStore recentViewStore;

	/** 홈 피드. 차단 관계와 삭제된 게시물은 쿼리에서 빠진다. 커서 키는 게시일이다. */
	@Loggable("홈 피드 조회")
	public CursorSlice<PostSummaryResponse> getFeed(
			Long userId, FeedTab tab, Cursor cursor, int size
	) {

		CursorSlice<Post> slice = CursorSlice.of(
				postRepository.findFeed(userId, tab, cursor, size),
				size, PostQueryService::byCreatedAt
		);

		if (slice.isEmpty()) {
			return CursorSlice.empty();
		}

		/**
		 * 페이징 된 slice 에서 추출할 목록들
		 * 게시물id, 대표 사진 url, 하트 게시물 id, 저장 게시물 Id
		 */
		List<Long> postIds = postIdsOf(slice);
		Map<Long, String> coverUrls = coverUrls(postIds);
		Set<Long> likedPostIds = likedPostIds(userId, postIds);
		Set<Long> savedPostIds = savedPostIds(userId, postIds);

		return slice.map(post ->
				postMapper.toPostSummaryResponse(
					post,
					coverUrls.get(post.getId()),
					likedPostIds.contains(post.getId()),
					savedPostIds.contains(post.getId())
				));
	}

	/**
	 * OOTD 상세.
	 *
	 * 부수효과로 최근 열람을 기록한다 — 검색 화면의 '방금 보신 OOTD와 유사한 게시물' 입력이다
	 * Redis 라 트랜잭션에 얹히지 않으므로 맨 끝에서 부른다.
	 */
	@Loggable("OOTD 상세 조회")
	public PostResponse getPost(Long userId, Long postId) {

		Post post = postRepository.findActiveWithAuthor(postId)
				.orElseThrow(() -> {
					log.warn("존재하지 않거나 삭제된 게시물입니다. postId={}", postId);
					return new CustomException(PostErrorCode.POST_404);
				});

		// 게시물에 달린 태그 조회 (태그에는 아이템과 사진이 같이 있음)
		List<PostItemTag> tags = postItemTagRepository.findAllByPostId(postId);

		// Map<ItemListingId, ItemListing> - 게시물의 아이템의 현재 상태(미판매, 판매중, 판매완료)
		Map<Long, ItemListing> latestListings = latestListings(itemIdsOf(tags));

		// 말풍선은 사진에 속한다 — 사진별로 묶어 두고 사진을 그릴 때 꺼낸다
		Map<Long, List<PostTagResponse>> tagsByImageId = new LinkedHashMap<>();

		for (PostItemTag tag : tags) {
			ItemListing listing = latestListings.get(tag.getItem().getId());
			tagsByImageId
					.computeIfAbsent(tag.getPostImage().getId(), key -> new ArrayList<>())
					.add(postMapper.toPostTagResponse(
							tag, listing == null ? null : listing.getSaleStatus()));
		}

		List<PostImageResponse> images = postImageRepository.findAllByPostId(postId).stream()
				.map(image -> postMapper.toPostImageResponse(
						image,
						toUrl(image.getImageKey()),
						tagsByImageId.getOrDefault(image.getId(), List.of())))
				.toList();

		User author = post.getAuthor();
		boolean following = !post.isAuthor(userId)
				&& followRepository.existsFollow(userId, author.getId());

		PostResponse response = postMapper.toPostResponse(
				post,
				postMapper.toAuthorResponse(author, toUrl(author.getProfileImageKey())),
				images,
				following,
				postLikeRepository.existsByPostIdAndUserId(postId, userId),
				postSaveRepository.existsByPostIdAndUserId(postId, userId)
		);

		recentViewStore.add(userId, RecentViewTargetType.POST, postId);

		return response;
	}

	/**
	 * 사진 속 태그된 아이템 목록. 단위가 게시물이 아니라 사진이다 (DEC-104).
	 *
	 * 삭제된 아이템도 내려준다 — 화면이 회색 처리해 '삭제된 아이템' 으로 보여준다.
	 */
	@Loggable("태그된 아이템 목록 조회")
	public List<TaggedItemResponse> getTaggedItems(Long userId, Long imageId) {

		postImageRepository.findActiveWithPost(imageId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 사진입니다. imageId={}", imageId);
					return new CustomException(PostErrorCode.POST_404_IMAGE);
				});

		List<PostItemTag> tags = postItemTagRepository.findAllByPostImageId(imageId);
		List<Long> itemIds = itemIdsOf(tags);

		Map<Long, ItemListing> latestListings = latestListings(itemIds);
		Set<Long> alarmEnabledItemIds = alarmEnabledItemIds(userId, itemIds);

		return tags.stream()
				.map(PostItemTag::getItem)
				.map(item -> {
					ItemListing listing = latestListings.get(item.getId());
					return postMapper.toTaggedItemResponse(
							item,
							listing,
							toUrl(item.getCoverImageKey()),
							taggedItemStatus(item, listing),
							alarmEnabledItemIds.contains(item.getId())
					);
				})
				.toList();
	}

	/** 마이페이지의 게시물 · 좋아요 · 저장 탭. 커서 키는 착용일이다 — 그리드가 월 헤더로 묶인다. */
	@Loggable("내 게시물 조회")
	public CursorSlice<MyPostSummaryResponse> getMyPosts(
			Long userId, MyPostTab tab, YearMonth yearMonth, Cursor cursor, int size
	) {
		CursorSlice<Post> slice = CursorSlice.of(
				postRepository.findMyPosts(userId, tab, yearMonth, cursor, size),
				size, PostQueryService::byWornDate);
		if (slice.isEmpty()) {
			return CursorSlice.empty();
		}

		List<Long> postIds = postIdsOf(slice);
		Map<Long, String> coverUrls = coverUrls(postIds);
		Set<Long> likedPostIds = likedPostIds(userId, postIds);
		Set<Long> savedPostIds = savedPostIds(userId, postIds);

		return slice.map(post -> postMapper.toMyPostSummaryResponse(
				post,
				coverUrls.get(post.getId()),
				likedPostIds.contains(post.getId()),
				savedPostIds.contains(post.getId())));
	}

	/**
	 * 타인 프로필의 게시물 그리드.
	 *
	 * 차단 관계면 빈 목록이다 — 404 로 돌려주면 '차단했다' 는 사실이 드러난다.
	 */
	@Loggable("타인 게시물 조회")
	public CursorSlice<UserPostSummaryResponse> getUserPosts(
			Long viewerId, Long targetUserId, Cursor cursor, int size
	) {
		if (!userRepository.existsById(targetUserId)) {
			log.warn("존재하지 않는 사용자입니다. userId={}", targetUserId);
			throw new CustomException(UserErrorCode.USER_404);
		}

		if (userBlockRepository.existsBlock(viewerId, targetUserId)
				|| userBlockRepository.existsBlock(targetUserId, viewerId)) {
			return CursorSlice.empty();
		}

		CursorSlice<Post> slice = CursorSlice.of(
				postRepository.findUserPosts(targetUserId, cursor, size),
				size, PostQueryService::byWornDate);
		if (slice.isEmpty()) {
			return CursorSlice.empty();
		}

		List<Long> postIds = postIdsOf(slice);
		Map<Long, String> coverUrls = coverUrls(postIds);
		Set<Long> likedPostIds = likedPostIds(viewerId, postIds);
		Set<Long> savedPostIds = savedPostIds(viewerId, postIds);

		// 남의 게시물에도 내가 좋아요·저장할 수 있다. 그리드 카드에 그 아이콘이 붙는다
		return slice.map(post -> postMapper.toUserPostSummaryResponse(
				post,
				coverUrls.get(post.getId()),
				likedPostIds.contains(post.getId()),
				savedPostIds.contains(post.getId())));
	}

	/**
	 * 표시 상태. 판매 등록의 상태와 아이템의 삭제 여부를 한 값으로 합친다.
	 *
	 * 판매 등록이 없으면 '미판매' 다 — 판매 전환을 한 번도 하지 않은 아이템이며, 이때는
	 * listingId 가 null 이라 화면이 CTA 를 띄우지 않는다.
	 */
	private TaggedItemStatus taggedItemStatus(Item item, ItemListing listing) {
		if (item.getDeletedAt() != null) {
			return TaggedItemStatus.DELETED;
		}
		if (listing == null) {
			return TaggedItemStatus.NOT_FOR_SALE;
		}
		return switch (listing.getSaleStatus()) {
			case ON_SALE -> TaggedItemStatus.ON_SALE;
			case SOLD -> TaggedItemStatus.SOLD;
			case NOT_FOR_SALE -> TaggedItemStatus.NOT_FOR_SALE;
		};
	}

	// slice 목록에서 postId만 담은 List 추춣
	private List<Long> postIdsOf(CursorSlice<Post> slice) {
		return slice.getContent().stream().map(Post::getId).toList();
	}

	/** 피드는 게시일 순이라 커서도 게시일로 잡는다 — 정렬 키와 달라지면 경계가 어긋난다. */
	private static Cursor byCreatedAt(Post post) {
		return Cursor.of(post.getCreatedAt(), post.getId());
	}

	/** 그리드는 착용일 순이다. */
	private static Cursor byWornDate(Post post) {
		return Cursor.of(post.getWornDate(), post.getId());
	}

	// 태스그된 아이템의 id List
	private List<Long> itemIdsOf(List<PostItemTag> tags) {
		return tags.stream().map(tag -> tag.getItem().getId()).distinct().toList();
	}

	/** 목록의 대표 사진. id 를 모아 한 번에 받는다. */
	private Map<Long, String> coverUrls(List<Long> postIds) {
		if (postIds.isEmpty()) {
			return Map.of();
		}
		return postImageRepository.findCovers(postIds).stream()
				.collect(Collectors.toMap(
						image -> image.getPost().getId(),
						image -> toUrl(image.getImageKey())
						)
				);
	}

	/** 아이템별 최신 판매 등록. 없으면 키가 없다. */
	private Map<Long, ItemListing> latestListings(Collection<Long> itemIds) {
		if (itemIds.isEmpty()) {
			return Map.of();
		}
		return itemListingRepository.findLatestByItemIds(itemIds).stream()
				.collect(Collectors.toMap(
						listing -> listing.getItem().getId(), Function.identity()));
	}

	// 하트를 누른 게시물의 id Set
	private Set<Long> likedPostIds(Long userId, List<Long> postIds) {
		return postIds.isEmpty()
				? Set.of()
				: new HashSet<>(postLikeRepository.findLikedPostIds(userId, postIds));
	}

	// 저장을 누른 게시물의 id Set
	private Set<Long> savedPostIds(Long userId, List<Long> postIds) {
		return postIds.isEmpty()
				? Set.of()
				: new HashSet<>(postSaveRepository.findSavedPostIds(userId, postIds));
	}

	// 위시 등록한 아이템의 id Set
	private Set<Long> alarmEnabledItemIds(Long userId, List<Long> itemIds) {
		return itemIds.isEmpty()
				? Set.of()
				: new HashSet<>(wishlistRepository.findAlarmEnabledItemIds(userId, itemIds));
	}

	/** 저장소 키를 화면에서 쓸 URL 로 바꾼다. 키가 없으면 null 이다 (기본 프로필 사진 등). */
	private String toUrl(String key) {
		return key == null ? null : fileStorage.toUrl(key);
	}
}
