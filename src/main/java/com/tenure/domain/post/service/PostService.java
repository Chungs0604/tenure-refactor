package com.tenure.domain.post.service;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.entity.ItemWearRecord;
import com.tenure.domain.item.enums.WearRecordVoidReason;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.item.repository.ItemWearRecordRepository;
import com.tenure.domain.listing.repository.ListingFeaturedPostRepository;
import com.tenure.domain.post.dto.request.PostCreateRequest;
import com.tenure.domain.post.dto.request.PostCreateRequest.PostImageRequest;
import com.tenure.domain.post.dto.request.PostCreateRequest.PostTagRequest;
import com.tenure.domain.post.dto.request.PostUpdateRequest;
import com.tenure.domain.post.dto.request.PostUpdateRequest.PostTagEditRequest;
import com.tenure.domain.post.dto.response.PostCreateResponse;
import com.tenure.domain.post.dto.response.PostIdResponse;
import com.tenure.domain.post.dto.response.PostLikeResponse;
import com.tenure.domain.post.dto.response.PostSaveResponse;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.entity.PostImage;
import com.tenure.domain.post.entity.PostItemTag;
import com.tenure.domain.post.entity.PostLike;
import com.tenure.domain.post.entity.PostSave;
import com.tenure.domain.post.exception.PostErrorCode;
import com.tenure.domain.post.repository.PostImageRepository;
import com.tenure.domain.post.repository.PostItemTagRepository;
import com.tenure.domain.post.repository.PostLikeRepository;
import com.tenure.domain.post.repository.PostRepository;
import com.tenure.domain.post.repository.PostSaveRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.domain.user.service.UserGradeService;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OOTD 게시와 태그 수정, 삭제, 좋아요·저장.
 *
 * 게시는 세 가지를 한 트랜잭션에서 한다 — 게시물·사진·태그 저장, 착용 기록 적립, 등급 판정.
 * 태그가 곧 'OOTD 인증' 이라 따로 떼면 인증이 없는 게시물이나 게시물이 없는 인증이 생긴다.
 *
 * 착용 기록은 아이템당 1건이다. 같은 아이템을 여러 사진에 태그해도 인증은 한 번인데,
 * uk_item_wear_record(item_id, post_id) 가 그 규칙을 DB 에서도 잡고 있다.
 *
 * 예외를 던지기 전에 판단 근거를 남긴다 — ErrorCode 자체는 ServiceLoggingAspect 가 찍는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

	/** 사진당 태그 상한. policies.max_tags_per_image */
	private static final int MAX_TAGS_PER_IMAGE = 5;

	private final PostRepository postRepository;
	private final PostImageRepository postImageRepository;
	private final PostItemTagRepository postItemTagRepository;
	private final PostLikeRepository postLikeRepository;
	private final PostSaveRepository postSaveRepository;
	private final ItemRepository itemRepository;
	private final ItemOwnershipRepository itemOwnershipRepository;
	private final ItemWearRecordRepository itemWearRecordRepository;
	private final ListingFeaturedPostRepository listingFeaturedPostRepository;
	private final UserRepository userRepository;
	private final UserGradeService userGradeService;

	/**
	 * OOTD 게시.
	 *
	 * 태그 대상은 게시 시점에 본인이 보유한 아이템만이다. 게시 후 그 아이템을 팔아도 과거 태그는
	 * 그대로 남는다 — 그게 '기록 승계' 의 근거다.
	 */
	@Transactional
	@Loggable("OOTD 게시")
	public PostCreateResponse create(Long userId, PostCreateRequest request) {
		User author = findUser(userId);

		validateImages(request.getImages());

		Post post = postRepository.save(Post.publish(
				author,
				request.getWornDate(),
				request.getAspectRatio(),
				Boolean.TRUE.equals(request.getQuickPosted())
		));

		// 아이템당 1건만 적립하므로 태그를 붙이며 모아 둔다. 순서를 유지해 로그를 읽기 쉽게 한다
		Map<Long, ItemOwnership> taggedOwnerships = new LinkedHashMap<>();

		List<PostImageRequest> imageRequests = request.getImages();
		boolean coverGiven = imageRequests.stream().anyMatch(image -> Boolean.TRUE.equals(image.getIsCover()));

		Set<Long> itemIds = new LinkedHashSet<>();
		for (PostImageRequest imageRequest : imageRequests) {
			for (PostTagRequest tagRequest : tagsOf(imageRequest)) {
				itemIds.add(tagRequest.getItemId());
			}
		}
		Map<Long, ItemOwnership> ownerships = findCurrentOwnerships(userId, itemIds);

		for (int index = 0; index < imageRequests.size(); index++) {
			PostImageRequest imageRequest = imageRequests.get(index);

			// 대표를 지정하지 않으면 첫 장을 쓴다 — 목록 카드가 대표 사진 한 장으로 그려진다
			boolean cover = coverGiven ? Boolean.TRUE.equals(imageRequest.getIsCover()) : index == 0;

			PostImage image = postImageRepository.save(PostImage.of(
					post, imageRequest.getImageKey(), imageRequest.getSortOrder(), cover
			));

			for (PostTagRequest tagRequest : tagsOf(imageRequest)) {
				ItemOwnership ownership = ownerships.get(tagRequest.getItemId());

				postItemTagRepository.save(PostItemTag.of(
						image, ownership.getItem(), tagRequest.getPosX(), tagRequest.getPosY()
				));
				taggedOwnerships.putIfAbsent(tagRequest.getItemId(), ownership);
			}
		}

		taggedOwnerships.values().forEach(ownership -> itemWearRecordRepository.save(
				ItemWearRecord.certify(
						ownership.getItem(), ownership, post, post.getWornDate()
				)));

		author.increasePostCount();

		// 승격 판정은 이 게시물까지 세야 하므로 저장 뒤에 한다 — 카운트 쿼리가 flush 를 유발한다
		boolean gradePromoted = userGradeService.evaluateOnPost(author);

		return PostCreateResponse.builder()
				.id(post.getId())
				.gradePromoted(gradePromoted)
				.build();
	}

	/**
	 * 태그 수정. 사진과 착용일은 바꿀 수 없다.
	 *
	 * 태그를 A→B 로 바꾸면 A 의 인증을 무효화하고 B 에 적립한다. 아이템을 비우면 태그를 지우고
	 * 무효화만 한다. 이미 판매돼 종료된 소유 구간의 인증은 손대지 못한다 — 구매자가 보고 산
	 * 'OOTD 인증 N회' 가 사후에 줄어들기 때문이다.
	 */
	@Transactional
	@Loggable("게시물 태그 수정")
	public PostIdResponse updateTags(Long userId, Long postId, PostUpdateRequest request) {
		Post post = findOwnedPost(userId, postId);

		Map<Long, PostItemTag> tags = new LinkedHashMap<>();
		postItemTagRepository.findAllByPostId(postId).forEach(tag -> tags.put(tag.getId(), tag));

		List<PostTagEditRequest> edits = request.getTags();

		// 아이템이 바뀌는 태그만 소유 자격이 필요하다 — 그 itemId 를 모아 한 번에 읽는다
		Set<Long> itemIdsToOwn = new LinkedHashSet<>();
		for (PostTagEditRequest edit : edits) {
			PostItemTag tag = tags.get(edit.getPostItemTagId());
			Long afterItemId = edit.getItemId();
			if (tag != null && afterItemId != null && !afterItemId.equals(tag.getItem().getId())) {
				itemIdsToOwn.add(afterItemId);
			}
		}
		Map<Long, ItemOwnership> ownerships = findCurrentOwnerships(userId, itemIdsToOwn);

		for (PostTagEditRequest edit : edits) {
			PostItemTag tag = tags.get(edit.getPostItemTagId());
			if (tag == null) {
				log.warn("이 게시물의 태그가 아닙니다. postId={}, postItemTagId={}",
						postId, edit.getPostItemTagId());
				throw new CustomException(PostErrorCode.POST_404_TAG);
			}

			Long beforeItemId = tag.getItem().getId();
			Long afterItemId = edit.getItemId();

			if (afterItemId == null) {
				postItemTagRepository.delete(tag);
				invalidateIfNoLongerTagged(postId, beforeItemId, WearRecordVoidReason.TAG_DELETED);
				continue;
			}

			if (afterItemId.equals(beforeItemId)) {
				continue;
			}

			ItemOwnership ownership = ownerships.get(afterItemId);
			tag.changeItem(ownership.getItem());
			invalidateIfNoLongerTagged(postId, beforeItemId, WearRecordVoidReason.TAG_CHANGED);
			certifyAgain(post, ownership);
		}

		return PostIdResponse.builder()
				.id(post.getId())
				.build();
	}

	/**
	 * 게시물 삭제. soft delete 다.
	 *
	 * 판매 중인 상품의 대표 OOTD 로 걸려 있으면 거부한다 — 대표 OOTD 는 1개 이상이어야 해서
	 * 지우는 순간 판매 화면의 그 칸이 빈다. 이 게시물이 적립한 인증은 함께 무효화하되 종료된
	 * 소유 구간의 것은 남긴다.
	 */
	@Transactional
	@Loggable("게시물 삭제")
	public void delete(Long userId, Long postId) {
		Post post = findOwnedPost(userId, postId);

		if (listingFeaturedPostRepository.existsOnSaleByPostId(postId)) {
			log.warn("판매 중인 상품의 대표 OOTD 입니다. postId={}", postId);
			throw new CustomException(PostErrorCode.POST_409_FEATURED);
		}

		itemWearRecordRepository.findAllByPostId(postId).stream()
				.filter(record -> !record.isVoided())
				.filter(record -> !record.getOwnership().isClosed())
				.forEach(record -> record.invalidate(WearRecordVoidReason.TAG_DELETED));

		post.softDelete();
		post.getAuthor().decreasePostCount();
	}

	/** 좋아요. 이미 눌렀으면 409 다 — 화면의 토글 상태와 서버가 어긋났다는 뜻이다. */
	@Transactional
	@Loggable("좋아요")
	public PostLikeResponse like(Long userId, Long postId) {
		Post post = findActivePost(postId);

		if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
			log.warn("이미 좋아요한 게시물입니다. postId={}, userId={}", postId, userId);
			throw new CustomException(PostErrorCode.POST_409_LIKE);
		}

		postLikeRepository.save(PostLike.of(post, findUser(userId)));
		post.increaseLikeCount();

		return PostLikeResponse.builder()
				.likeCount(post.getLikeCount())
				.build();
	}

	/** 좋아요 취소. 없는 관계면 조용히 성공 처리한다 — 취소의 결과는 어느 쪽이든 '안 누른 상태' 다. */
	@Transactional
	@Loggable("좋아요 취소")
	public PostLikeResponse unlike(Long userId, Long postId) {
		Post post = findActivePost(postId);

		postLikeRepository.findByPostIdAndUserId(postId, userId).ifPresent(like -> {
			postLikeRepository.delete(like);
			post.decreaseLikeCount();
		});

		return PostLikeResponse.builder()
				.likeCount(post.getLikeCount())
				.build();
	}

	/** 저장. 좋아요와 탭이 분리돼 있어 테이블도 따로다. */
	@Transactional
	@Loggable("게시물 저장")
	public PostSaveResponse save(Long userId, Long postId) {
		Post post = findActivePost(postId);

		if (postSaveRepository.existsByPostIdAndUserId(postId, userId)) {
			log.warn("이미 저장한 게시물입니다. postId={}, userId={}", postId, userId);
			throw new CustomException(PostErrorCode.POST_409_SAVE);
		}

		postSaveRepository.save(PostSave.of(post, findUser(userId)));
		post.increaseSaveCount();

		return PostSaveResponse.builder()
				.saveCount(post.getSaveCount())
				.build();
	}

	/** 저장 취소. */
	@Transactional
	@Loggable("게시물 저장 취소")
	public PostSaveResponse unsave(Long userId, Long postId) {
		Post post = findActivePost(postId);

		postSaveRepository.findByPostIdAndUserId(postId, userId).ifPresent(saved -> {
			postSaveRepository.delete(saved);
			post.decreaseSaveCount();
		});

		return PostSaveResponse.builder()
				.saveCount(post.getSaveCount())
				.build();
	}

	/**
	 * 조회수 배치 반영 + 추천 순 정렬 키 재계산. {@code PostSearchMetricsScheduler} 가 Redis
	 * 조회(peek)·이 메서드·Redis 삭제(clear) 순서로 호출한다 — Redis 읽기/삭제를 이 메서드
	 * 밖에 두는 이유가 둘이다. (1) DB 트랜잭션 안에서 Redis 왕복을 하면 그 시간만큼 DB
	 * 커넥션을 붙들게 된다. (2) 같은 클래스 안에서 이 메서드를 호출하면(self-invocation)
	 * {@code @Transactional} 이 스프링 프록시를 안 타 조용히 무시되므로, 애초에 트랜잭션
	 * 경계가 필요한 지점을 별도 빈(스케줄러)이 호출하는 공개 메서드로 둔다.
	 *
	 * 증가·재계산 순서도 중요하다 — rank_score 계산식이 view_count 를 쓰므로, 조회수를
	 * 먼저 반영해야 이번 주기에 쌓인 조회가 이번 주기의 점수에 들어간다.
	 */
	@Transactional
	@Loggable("검색 정렬 지표 배치")
	public void applySearchMetrics(Map<Long, Long> viewDeltas) {
		viewDeltas.forEach(postRepository::incrementViewCount);

		postRepository.recalculateRankScores(
				RankScorePolicy.SAVE_WEIGHT,
				RankScorePolicy.LIKE_WEIGHT,
				RankScorePolicy.VIEW_WEIGHT,
				RankScorePolicy.POPULARITY_WEIGHT,
				RankScorePolicy.FRESHNESS_WEIGHT,
				RankScorePolicy.FRESHNESS_HALF_LIFE_DAYS);
	}

	/**
	 * 사진 구성 검증.
	 *
	 * 순서 중복과 대표 2장은 DB 제약(uk_post_image_order)이나 목록 조회에서 터지는데,
	 * 그때는 500 이나 '카드가 두 번 보이는' 형태로 드러나 원인을 찾기 어렵다.
	 */
	private void validateImages(List<PostImageRequest> images) {
		Set<Integer> sortOrders = new HashSet<>();
		for (PostImageRequest image : images) {
			if (!sortOrders.add(image.getSortOrder())) {
				log.warn("사진 순서가 중복됐습니다. sortOrder={}", image.getSortOrder());
				throw new CustomException(PostErrorCode.POST_400_IMAGE_ORDER);
			}
		}

		long coverCount = images.stream()
				.filter(image -> Boolean.TRUE.equals(image.getIsCover()))
				.count();
		if (coverCount > 1) {
			log.warn("대표 사진을 여러 장 지정했습니다. coverCount={}", coverCount);
			throw new CustomException(PostErrorCode.POST_400_COVER);
		}

		for (PostImageRequest image : images) {
			int tagCount = tagsOf(image).size();
			if (tagCount > MAX_TAGS_PER_IMAGE) {
				log.warn("사진당 태그 상한을 넘었습니다. tagCount={}", tagCount);
				throw new CustomException(PostErrorCode.POST_400_TAG_LIMIT);
			}
		}
	}

	private List<PostTagRequest> tagsOf(PostImageRequest image) {
		return image.getTags() == null ? List.of() : image.getTags();
	}

	/**
	 * 태그 자격 일괄 확인. 본인이 현재 보유한 아이템이어야 한다.
	 *
	 * 이미지×태그 이중 루프에서 태그마다 아이템·소유 구간을 따로 조회하면 쿼리가 태그 수만큼
	 * 는다 — 요청에 등장한 itemId 를 모아 한 번에 읽고 맵으로 캐싱해 쓴다.
	 *
	 * 소유 구간을 함께 돌려주는 이유는 인증이 구간별로 집계되기 때문이다 — 어느 구간에 적립하는지가
	 * 화면의 'OOTD 인증 N회' 를 가른다.
	 */
	private Map<Long, ItemOwnership> findCurrentOwnerships(Long userId, Collection<Long> itemIds) {
		if (itemIds.isEmpty()) {
			return Map.of();
		}

		Set<Long> activeItemIds = itemRepository.findAllByIds(itemIds).stream()
				.filter(item -> item.getDeletedAt() == null)
				.map(Item::getId)
				.collect(Collectors.toSet());

		Map<Long, ItemOwnership> ownershipsByItemId = itemOwnershipRepository
				.findCurrentByItemIds(itemIds).stream()
				.collect(Collectors.toMap(ownership -> ownership.getItem().getId(), Function.identity()));

		Map<Long, ItemOwnership> result = new LinkedHashMap<>();
		for (Long itemId : itemIds) {
			if (!activeItemIds.contains(itemId)) {
				log.warn("존재하지 않거나 삭제된 아이템입니다. itemId={}", itemId);
				throw new CustomException(ItemErrorCode.ITEM_404);
			}

			ItemOwnership ownership = ownershipsByItemId.get(itemId);
			if (ownership == null) {
				log.warn("현재 소유 구간이 없는 아이템입니다. itemId={}", itemId);
				throw new CustomException(PostErrorCode.POST_400_TAG_OWNERSHIP);
			}

			if (!ownership.getOwner().getId().equals(userId)) {
				log.warn("본인이 보유한 아이템이 아닙니다. itemId={}, userId={}", itemId, userId);
				throw new CustomException(PostErrorCode.POST_400_TAG_OWNERSHIP);
			}

			result.put(itemId, ownership);
		}
		return result;
	}

	/**
	 * 이 게시물의 다른 사진에 같은 아이템이 아직 태그돼 있으면 인증을 건드리지 않는다.
	 *
	 * 같은 아이템을 두 사진에 태그해도 인증은 한 번만 쌓이는데, 그중 한 태그만 지우거나 바꿀 때
	 * 남은 태그를 보지 않고 무조건 무효화하면 '내 옷장'의 인증 횟수가 부당하게 줄어든다.
	 */
	private void invalidateIfNoLongerTagged(Long postId, Long itemId, WearRecordVoidReason reason) {
		if (postItemTagRepository.countByPostIdAndItemId(postId, itemId) > 0) {
			return;
		}
		invalidateRecord(postId, itemId, reason);
	}

	/** 태그가 떨어진 아이템의 인증을 무효화한다. 종료된 구간이면 수정 자체를 막는다. */
	private void invalidateRecord(Long postId, Long itemId, WearRecordVoidReason reason) {
		Optional<ItemWearRecord> found = itemWearRecordRepository.findByPostIdAndItemId(postId, itemId);
		if (found.isEmpty()) {
			return;
		}

		ItemWearRecord record = found.get();
		if (record.getOwnership().isClosed()) {
			log.warn("종료된 소유 구간의 태그입니다. postId={}, itemId={}", postId, itemId);
			throw new CustomException(ItemErrorCode.ITEM_409_TAG_CLOSED_PERIOD);
		}

		record.invalidate(reason);
	}

	/**
	 * 새로 붙은 아이템에 인증을 적립한다.
	 *
	 * uk_item_wear_record(item_id, post_id) 때문에 무효화된 행이 남아 있으면 INSERT 가
	 * 중복 키로 실패한다. 있으면 되살리고 없을 때만 새로 만든다.
	 */
	private void certifyAgain(Post post, ItemOwnership ownership) {
		itemWearRecordRepository
				.findByPostIdAndItemId(post.getId(), ownership.getItem().getId())
				.ifPresentOrElse(
						ItemWearRecord::restore,
						() -> itemWearRecordRepository.save(ItemWearRecord.certify(
								ownership.getItem(), ownership, post, post.getWornDate()
						)));
	}

	/** 남의 게시물을 고치지 못하게 막는다. */
	private Post findOwnedPost(Long userId, Long postId) {
		Post post = findActivePost(postId);

		if (!post.isAuthor(userId)) {
			log.warn("본인의 게시물이 아닙니다. postId={}, userId={}", postId, userId);
			throw new CustomException(PostErrorCode.POST_403);
		}

		return post;
	}

	private Post findActivePost(Long postId) {
		return postRepository.findActive(postId)
				.orElseThrow(() -> {
					log.warn("존재하지 않거나 삭제된 게시물입니다. postId={}", postId);
					return new CustomException(PostErrorCode.POST_404);
				});
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 사용자입니다. userId={}", userId);
					return new CustomException(UserErrorCode.USER_404);
				});
	}
}
