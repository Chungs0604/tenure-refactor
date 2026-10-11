package com.tenure.domain.post.service;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.entity.ItemWearRecord;
import com.tenure.domain.item.enums.WearRecordVoidReason;
import com.tenure.domain.item.enums.WearTarget;
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
import com.tenure.domain.post.dto.response.PostLikeResponse;
import com.tenure.domain.post.dto.response.PostSaveResponse;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.entity.PostImage;
import com.tenure.domain.post.entity.PostItemTag;
import com.tenure.domain.post.entity.PostLike;
import com.tenure.domain.post.entity.PostSave;
import com.tenure.domain.post.enums.AspectRatio;
import com.tenure.domain.post.exception.PostErrorCode;
import com.tenure.domain.post.repository.PostImageRepository;
import com.tenure.domain.post.repository.PostItemTagRepository;
import com.tenure.domain.post.repository.PostLikeRepository;
import com.tenure.domain.post.repository.PostRepository;
import com.tenure.domain.post.repository.PostSaveRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.domain.user.service.UserGradeService;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PostService")
class PostServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long OTHER_USER_ID = 2L;
	private static final Long POST_ID = 10L;
	private static final Long ITEM_ID = 100L;
	private static final Long OTHER_ITEM_ID = 200L;
	private static final Long TAG_ID = 1000L;

	@Mock private PostRepository postRepository;
	@Mock private PostImageRepository postImageRepository;
	@Mock private PostItemTagRepository postItemTagRepository;
	@Mock private PostLikeRepository postLikeRepository;
	@Mock private PostSaveRepository postSaveRepository;
	@Mock private ItemRepository itemRepository;
	@Mock private ItemOwnershipRepository itemOwnershipRepository;
	@Mock private ItemWearRecordRepository itemWearRecordRepository;
	@Mock private ListingFeaturedPostRepository listingFeaturedPostRepository;
	@Mock private UserRepository userRepository;
	@Mock private UserGradeService userGradeService;

	@InjectMocks private PostService postService;

	@Captor private ArgumentCaptor<PostImage> imageCaptor;
	@Captor private ArgumentCaptor<PostItemTag> tagCaptor;
	@Captor private ArgumentCaptor<ItemWearRecord> recordCaptor;

	@Nested
	@DisplayName("OOTD 게시")
	class Create {

		@Test
		@DisplayName("사진과 태그를 저장하고 게시물 id 를 돌려준다")
		void create_withTags_savesImagesAndTags() {
			User author = givenAuthor();
			givenSavedPost(author);
			givenOwnedItem();

			PostCreateResponse response = postService.create(USER_ID, createRequest());

			assertThat(response.getId()).isEqualTo(POST_ID);
			verify(postImageRepository).save(imageCaptor.capture());
			assertThat(imageCaptor.getValue().getImageKey()).isEqualTo("post/2026/09/a.jpg");
			verify(postItemTagRepository).save(tagCaptor.capture());
			assertThat(tagCaptor.getValue().getItem().getId()).isEqualTo(ITEM_ID);
		}

		@Test
		@DisplayName("태그가 붙으면 그 자리에서 착용 기록을 적립한다 — 태그가 곧 OOTD 인증이다")
		void create_withTags_certifiesWearRecord() {
			User author = givenAuthor();
			Post post = givenSavedPost(author);
			ItemOwnership ownership = givenOwnedItem();

			postService.create(USER_ID, createRequest());

			verify(itemWearRecordRepository).save(recordCaptor.capture());
			ItemWearRecord record = recordCaptor.getValue();
			assertThat(record.getItem().getId()).isEqualTo(ITEM_ID);
			assertThat(record.getOwnership()).isEqualTo(ownership);
			// 착용일은 게시물의 값을 복사한다 (DEC-013)
			assertThat(record.getWornDate()).isEqualTo(post.getWornDate());
		}

		@Test
		@DisplayName("같은 아이템을 두 사진에 태그해도 인증은 1건이다")
		void create_sameItemInTwoImages_certifiesOnce() {
			User author = givenAuthor();
			givenSavedPost(author);
			givenOwnedItem();

			PostCreateRequest request = PostCreateRequest.builder()
					.wornDate(LocalDate.now())
					.aspectRatio(AspectRatio.RATIO_3_4)
					.images(List.of(
							imageRequest("post/2026/09/a.jpg", 0, true, List.of(tagRequest(ITEM_ID))),
							imageRequest("post/2026/09/b.jpg", 1, false, List.of(tagRequest(ITEM_ID)))))
					.build();

			postService.create(USER_ID, request);

			verify(postItemTagRepository, times(2)).save(any(PostItemTag.class));
			verify(itemWearRecordRepository, times(1)).save(any(ItemWearRecord.class));
		}

		@Test
		@DisplayName("대표 사진을 지정하지 않으면 첫 장이 대표가 된다")
		void create_noCoverGiven_firstImageBecomesCover() {
			User author = givenAuthor();
			givenSavedPost(author);

			PostCreateRequest request = PostCreateRequest.builder()
					.wornDate(LocalDate.now())
					.aspectRatio(AspectRatio.RATIO_1_1)
					.images(List.of(
							imageRequest("post/2026/09/a.jpg", 0, null, null),
							imageRequest("post/2026/09/b.jpg", 1, null, null)))
					.build();

			postService.create(USER_ID, request);

			verify(postImageRepository, times(2)).save(imageCaptor.capture());
			assertThat(imageCaptor.getAllValues().get(0).getIsCover()).isTrue();
			assertThat(imageCaptor.getAllValues().get(1).getIsCover()).isFalse();
		}

		@Test
		@DisplayName("게시물 수를 올리고 등급 승격 여부를 함께 돌려준다")
		void create_reachedThreshold_returnsGradePromoted() {
			User author = givenAuthor();
			givenSavedPost(author);
			givenOwnedItem();
			given(userGradeService.evaluateOnPost(author)).willReturn(true);

			PostCreateResponse response = postService.create(USER_ID, createRequest());

			assertThat(response.getGradePromoted()).isTrue();
			assertThat(author.getPostCount()).isEqualTo(1);
		}

		@Test
		@DisplayName("남의 아이템을 태그하면 POST_400_TAG_OWNERSHIP 이다")
		void create_tagsNotOwnedItem_throwsOwnership() {
			User author = givenAuthor();
			givenSavedPost(author);

			Item item = item(otherUser());
			ReflectionTestUtils.setField(item, "id", ITEM_ID);
			given(itemRepository.findAllByIds(any())).willReturn(List.of(item));
			given(itemOwnershipRepository.findCurrentByItemIds(any()))
					.willReturn(List.of(ownership(item, otherUser())));

			assertThatThrownBy(() -> postService.create(USER_ID, createRequest()))
					.isInstanceOf(CustomException.class)
					.extracting(PostServiceTest::errorCodeOf)
					.isEqualTo(PostErrorCode.POST_400_TAG_OWNERSHIP);
		}

		@Test
		@DisplayName("삭제된 아이템은 태그할 수 없다 — ITEM_404")
		void create_tagsDeletedItem_throwsItemNotFound() {
			User author = givenAuthor();
			givenSavedPost(author);
			given(itemRepository.findAllByIds(any())).willReturn(List.of());

			assertThatThrownBy(() -> postService.create(USER_ID, createRequest()))
					.isInstanceOf(CustomException.class)
					.extracting(PostServiceTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_404);
		}

		@Test
		@DisplayName("사진당 태그가 5개를 넘으면 POST_400_TAG_LIMIT 이고 아무것도 저장하지 않는다")
		void create_tooManyTags_throwsTagLimit() {
			givenAuthor();

			PostCreateRequest request = PostCreateRequest.builder()
					.wornDate(LocalDate.now())
					.aspectRatio(AspectRatio.RATIO_3_4)
					.images(List.of(imageRequest("post/2026/09/a.jpg", 0, true, List.of(
							tagRequest(1L), tagRequest(2L), tagRequest(3L),
							tagRequest(4L), tagRequest(5L), tagRequest(6L)))))
					.build();

			assertThatThrownBy(() -> postService.create(USER_ID, request))
					.isInstanceOf(CustomException.class)
					.extracting(PostServiceTest::errorCodeOf)
					.isEqualTo(PostErrorCode.POST_400_TAG_LIMIT);

			verify(postRepository, times(0)).save(any(Post.class));
		}

		@Test
		@DisplayName("대표 사진을 두 장 지정하면 POST_400_COVER 다")
		void create_twoCovers_throwsCoverError() {
			givenAuthor();

			PostCreateRequest request = PostCreateRequest.builder()
					.wornDate(LocalDate.now())
					.aspectRatio(AspectRatio.RATIO_3_4)
					.images(List.of(
							imageRequest("post/2026/09/a.jpg", 0, true, null),
							imageRequest("post/2026/09/b.jpg", 1, true, null)))
					.build();

			assertThatThrownBy(() -> postService.create(USER_ID, request))
					.isInstanceOf(CustomException.class)
					.extracting(PostServiceTest::errorCodeOf)
					.isEqualTo(PostErrorCode.POST_400_COVER);
		}

		@Test
		@DisplayName("사진 순서가 중복되면 POST_400_IMAGE_ORDER 다 — 유니크 제약이 500 으로 터지기 전에 막는다")
		void create_duplicatedSortOrder_throwsOrderError() {
			givenAuthor();

			PostCreateRequest request = PostCreateRequest.builder()
					.wornDate(LocalDate.now())
					.aspectRatio(AspectRatio.RATIO_3_4)
					.images(List.of(
							imageRequest("post/2026/09/a.jpg", 0, true, null),
							imageRequest("post/2026/09/b.jpg", 0, false, null)))
					.build();

			assertThatThrownBy(() -> postService.create(USER_ID, request))
					.isInstanceOf(CustomException.class)
					.extracting(PostServiceTest::errorCodeOf)
					.isEqualTo(PostErrorCode.POST_400_IMAGE_ORDER);
		}
	}

	@Nested
	@DisplayName("태그 수정")
	class UpdateTags {

		@Test
		@DisplayName("A→B 로 바꾸면 A 의 인증을 TAG_CHANGED 로 무효화하고 B 에 적립한다")
		void updateTags_changeItem_voidsOldAndCertifiesNew() {
			User author = givenAuthor();
			Post post = givenExistingPost(author);
			Item oldItem = item(author);
			ReflectionTestUtils.setField(oldItem, "id", ITEM_ID);
			PostItemTag tag = givenExistingTag(post, oldItem);

			Item newItem = item(author);
			ReflectionTestUtils.setField(newItem, "id", OTHER_ITEM_ID);
			given(itemRepository.findAllByIds(any())).willReturn(List.of(newItem));
			given(itemOwnershipRepository.findCurrentByItemIds(any()))
					.willReturn(List.of(ownership(newItem, author)));

			ItemWearRecord oldRecord = ItemWearRecord.certify(
					oldItem, ownership(oldItem, author), post, post.getWornDate());
			given(itemWearRecordRepository.findByPostIdAndItemId(POST_ID, ITEM_ID))
					.willReturn(Optional.of(oldRecord));
			given(itemWearRecordRepository.findByPostIdAndItemId(POST_ID, OTHER_ITEM_ID))
					.willReturn(Optional.empty());

			postService.updateTags(USER_ID, POST_ID, updateRequest(OTHER_ITEM_ID));

			assertThat(oldRecord.isVoided()).isTrue();
			assertThat(oldRecord.getVoidedReason()).isEqualTo(WearRecordVoidReason.TAG_CHANGED);
			assertThat(tag.getItem().getId()).isEqualTo(OTHER_ITEM_ID);
			verify(itemWearRecordRepository).save(any(ItemWearRecord.class));
		}

		@Test
		@DisplayName("아이템을 비우면 태그를 지우고 TAG_DELETED 로 무효화한다")
		void updateTags_nullItem_deletesTag() {
			User author = givenAuthor();
			Post post = givenExistingPost(author);
			Item oldItem = item(author);
			ReflectionTestUtils.setField(oldItem, "id", ITEM_ID);
			PostItemTag tag = givenExistingTag(post, oldItem);

			ItemWearRecord oldRecord = ItemWearRecord.certify(
					oldItem, ownership(oldItem, author), post, post.getWornDate());
			given(itemWearRecordRepository.findByPostIdAndItemId(POST_ID, ITEM_ID))
					.willReturn(Optional.of(oldRecord));

			postService.updateTags(USER_ID, POST_ID, updateRequest(null));

			assertThat(oldRecord.getVoidedReason()).isEqualTo(WearRecordVoidReason.TAG_DELETED);
			verify(postItemTagRepository).delete(tag);
		}

		@Test
		@DisplayName("같은 아이템을 두 사진에 태그했다가 한쪽만 지워도 다른 사진에 남아있으면 인증을 유지한다")
		void updateTags_itemStillTaggedOnOtherImage_keepsRecord() {
			User author = givenAuthor();
			Post post = givenExistingPost(author);
			Item item = item(author);
			ReflectionTestUtils.setField(item, "id", ITEM_ID);

			PostImage image1 = PostImage.of(post, "post/2026/09/a.jpg", 0, true);
			ReflectionTestUtils.setField(image1, "id", 500L);
			PostImage image2 = PostImage.of(post, "post/2026/09/b.jpg", 1, false);
			ReflectionTestUtils.setField(image2, "id", 501L);

			PostItemTag tagOnImage1 = PostItemTag.of(image1, item, new BigDecimal("0.4200"), new BigDecimal("0.6100"));
			ReflectionTestUtils.setField(tagOnImage1, "id", TAG_ID);
			PostItemTag tagOnImage2 = PostItemTag.of(image2, item, new BigDecimal("0.3000"), new BigDecimal("0.5000"));
			ReflectionTestUtils.setField(tagOnImage2, "id", TAG_ID + 1);
			given(postItemTagRepository.findAllByPostId(POST_ID))
					.willReturn(List.of(tagOnImage1, tagOnImage2));

			// 지우는 태그 말고 다른 사진(tagOnImage2)에 같은 아이템이 아직 남아있다
			given(postItemTagRepository.countByPostIdAndItemId(POST_ID, ITEM_ID)).willReturn(1L);

			ItemWearRecord record = ItemWearRecord.certify(
					item, ownership(item, author), post, post.getWornDate());

			postService.updateTags(USER_ID, POST_ID, updateRequest(null));

			verify(postItemTagRepository).delete(tagOnImage1);
			assertThat(record.isVoided()).isFalse();
			verify(itemWearRecordRepository, times(0)).findByPostIdAndItemId(POST_ID, ITEM_ID);
		}

		@Test
		@DisplayName("이미 무효화된 인증이 있으면 새로 넣지 않고 되살린다 — 유니크 제약에 걸린다")
		void updateTags_voidedRecordExists_restoresInsteadOfInsert() {
			User author = givenAuthor();
			Post post = givenExistingPost(author);
			Item oldItem = item(author);
			ReflectionTestUtils.setField(oldItem, "id", ITEM_ID);
			givenExistingTag(post, oldItem);

			Item newItem = item(author);
			ReflectionTestUtils.setField(newItem, "id", OTHER_ITEM_ID);
			given(itemRepository.findAllByIds(any())).willReturn(List.of(newItem));
			given(itemOwnershipRepository.findCurrentByItemIds(any()))
					.willReturn(List.of(ownership(newItem, author)));

			ItemWearRecord voided = ItemWearRecord.certify(
					newItem, ownership(newItem, author), post, post.getWornDate());
			voided.invalidate(WearRecordVoidReason.TAG_CHANGED);
			given(itemWearRecordRepository.findByPostIdAndItemId(POST_ID, ITEM_ID))
					.willReturn(Optional.empty());
			given(itemWearRecordRepository.findByPostIdAndItemId(POST_ID, OTHER_ITEM_ID))
					.willReturn(Optional.of(voided));

			postService.updateTags(USER_ID, POST_ID, updateRequest(OTHER_ITEM_ID));

			assertThat(voided.isVoided()).isFalse();
			verify(itemWearRecordRepository, times(0)).save(any(ItemWearRecord.class));
		}

		@Test
		@DisplayName("종료된 소유 구간의 태그는 고칠 수 없다 — 구매자가 보고 산 인증 수가 줄어든다")
		void updateTags_closedOwnership_throwsConflict() {
			User author = givenAuthor();
			Post post = givenExistingPost(author);
			Item oldItem = item(author);
			ReflectionTestUtils.setField(oldItem, "id", ITEM_ID);
			givenExistingTag(post, oldItem);

			Item newItem = item(author);
			ReflectionTestUtils.setField(newItem, "id", OTHER_ITEM_ID);
			given(itemRepository.findAllByIds(any())).willReturn(List.of(newItem));
			given(itemOwnershipRepository.findCurrentByItemIds(any()))
					.willReturn(List.of(ownership(newItem, author)));

			ItemOwnership closed = ownership(oldItem, author);
			closed.close(LocalDate.now().minusDays(1));
			given(itemWearRecordRepository.findByPostIdAndItemId(POST_ID, ITEM_ID))
					.willReturn(Optional.of(ItemWearRecord.certify(
							oldItem, closed, post, post.getWornDate())));

			assertThatThrownBy(() -> postService.updateTags(USER_ID, POST_ID, updateRequest(OTHER_ITEM_ID)))
					.isInstanceOf(CustomException.class)
					.extracting(PostServiceTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_409_TAG_CLOSED_PERIOD);
		}

		@Test
		@DisplayName("이 게시물의 태그가 아니면 POST_404_TAG 다")
		void updateTags_unknownTag_throwsTagNotFound() {
			User author = givenAuthor();
			givenExistingPost(author);
			given(postItemTagRepository.findAllByPostId(POST_ID)).willReturn(List.of());

			assertThatThrownBy(() -> postService.updateTags(USER_ID, POST_ID, updateRequest(OTHER_ITEM_ID)))
					.isInstanceOf(CustomException.class)
					.extracting(PostServiceTest::errorCodeOf)
					.isEqualTo(PostErrorCode.POST_404_TAG);
		}

		@Test
		@DisplayName("남의 게시물이면 POST_403 이다")
		void updateTags_notAuthor_throwsForbidden() {
			givenExistingPost(otherUser());

			assertThatThrownBy(() -> postService.updateTags(USER_ID, POST_ID, updateRequest(ITEM_ID)))
					.isInstanceOf(CustomException.class)
					.extracting(PostServiceTest::errorCodeOf)
					.isEqualTo(PostErrorCode.POST_403);
		}
	}

	@Nested
	@DisplayName("게시물 삭제")
	class Delete {

		@Test
		@DisplayName("soft delete 하고 게시물 수를 내린다")
		void delete_ownPost_softDeletesAndDecreasesCount() {
			User author = givenAuthor();
			author.increasePostCount();
			Post post = givenExistingPost(author);
			given(itemWearRecordRepository.findAllByPostId(POST_ID)).willReturn(List.of());

			postService.delete(USER_ID, POST_ID);

			assertThat(post.isDeleted()).isTrue();
			assertThat(author.getPostCount()).isZero();
		}

		@Test
		@DisplayName("적립한 인증을 TAG_DELETED 로 무효화한다")
		void delete_hasWearRecords_voidsThem() {
			User author = givenAuthor();
			Post post = givenExistingPost(author);
			Item taggedItem = item(author);
			ItemWearRecord record = ItemWearRecord.certify(
					taggedItem, ownership(taggedItem, author), post, post.getWornDate());
			given(itemWearRecordRepository.findAllByPostId(POST_ID)).willReturn(List.of(record));

			postService.delete(USER_ID, POST_ID);

			assertThat(record.getVoidedReason()).isEqualTo(WearRecordVoidReason.TAG_DELETED);
		}

		@Test
		@DisplayName("종료된 소유 구간의 인증은 건드리지 않는다 — 이미 승계된 기록이다")
		void delete_closedOwnership_keepsRecord() {
			User author = givenAuthor();
			Post post = givenExistingPost(author);
			Item soldItem = item(author);
			ItemOwnership closed = ownership(soldItem, author);
			closed.close(LocalDate.now().minusDays(1));
			ItemWearRecord record = ItemWearRecord.certify(
					soldItem, closed, post, post.getWornDate());
			given(itemWearRecordRepository.findAllByPostId(POST_ID)).willReturn(List.of(record));

			postService.delete(USER_ID, POST_ID);

			assertThat(record.isVoided()).isFalse();
		}

		@Test
		@DisplayName("판매 중 상품의 대표 OOTD 면 POST_409_FEATURED 이고 삭제하지 않는다")
		void delete_featuredOnSale_throwsConflict() {
			User author = givenAuthor();
			Post post = givenExistingPost(author);
			given(listingFeaturedPostRepository
					.existsOnSaleByPostId(POST_ID)).willReturn(true);

			assertThatThrownBy(() -> postService.delete(USER_ID, POST_ID))
					.isInstanceOf(CustomException.class)
					.extracting(PostServiceTest::errorCodeOf)
					.isEqualTo(PostErrorCode.POST_409_FEATURED);

			assertThat(post.isDeleted()).isFalse();
		}
	}

	@Nested
	@DisplayName("좋아요 · 저장")
	class Reactions {

		@Test
		@DisplayName("좋아요하면 카운터를 올리고 갱신값을 돌려준다")
		void like_firstTime_increasesCount() {
			User author = givenAuthor();
			Post post = givenExistingPost(author);

			PostLikeResponse response = postService.like(USER_ID, POST_ID);

			assertThat(response.getLikeCount()).isEqualTo(1);
			assertThat(post.getLikeCount()).isEqualTo(1);
			verify(postLikeRepository).save(any(PostLike.class));
		}

		@Test
		@DisplayName("이미 좋아요했으면 POST_409_LIKE 다")
		void like_alreadyLiked_throwsConflict() {
			givenAuthor();
			givenExistingPost(author());
			given(postLikeRepository.existsByPostIdAndUserId(POST_ID, USER_ID)).willReturn(true);

			assertThatThrownBy(() -> postService.like(USER_ID, POST_ID))
					.isInstanceOf(CustomException.class)
					.extracting(PostServiceTest::errorCodeOf)
					.isEqualTo(PostErrorCode.POST_409_LIKE);

			verify(postLikeRepository, times(0)).save(any(PostLike.class));
		}

		@Test
		@DisplayName("좋아요를 취소하면 카운터를 내린다")
		void unlike_liked_decreasesCount() {
			User author = givenAuthor();
			Post post = givenExistingPost(author);
			post.increaseLikeCount();
			given(postLikeRepository.findByPostIdAndUserId(POST_ID, USER_ID))
					.willReturn(Optional.of(PostLike.of(post, author)));

			PostLikeResponse response = postService.unlike(USER_ID, POST_ID);

			assertThat(response.getLikeCount()).isZero();
			verify(postLikeRepository).delete(any(PostLike.class));
		}

		@Test
		@DisplayName("누르지 않은 좋아요를 취소해도 성공이고 카운터는 그대로다")
		void unlike_notLiked_succeedsWithoutChange() {
			User author = givenAuthor();
			Post post = givenExistingPost(author);
			post.increaseLikeCount();
			given(postLikeRepository.findByPostIdAndUserId(POST_ID, USER_ID)).willReturn(Optional.empty());

			PostLikeResponse response = postService.unlike(USER_ID, POST_ID);

			assertThat(response.getLikeCount()).isEqualTo(1);
			verify(postLikeRepository, times(0)).delete(any(PostLike.class));
		}

		@Test
		@DisplayName("저장하면 저장 수를 올린다")
		void save_firstTime_increasesCount() {
			User author = givenAuthor();
			Post post = givenExistingPost(author);

			PostSaveResponse response = postService.save(USER_ID, POST_ID);

			assertThat(response.getSaveCount()).isEqualTo(1);
			assertThat(post.getSaveCount()).isEqualTo(1);
			verify(postSaveRepository).save(any(PostSave.class));
		}

		@Test
		@DisplayName("이미 저장했으면 POST_409_SAVE 다")
		void save_alreadySaved_throwsConflict() {
			givenAuthor();
			givenExistingPost(author());
			given(postSaveRepository.existsByPostIdAndUserId(POST_ID, USER_ID)).willReturn(true);

			assertThatThrownBy(() -> postService.save(USER_ID, POST_ID))
					.isInstanceOf(CustomException.class)
					.extracting(PostServiceTest::errorCodeOf)
					.isEqualTo(PostErrorCode.POST_409_SAVE);
		}

		@Test
		@DisplayName("삭제된 게시물에는 좋아요할 수 없다 — POST_404")
		void like_deletedPost_throwsNotFound() {
			given(postRepository.findActive(POST_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> postService.like(USER_ID, POST_ID))
					.isInstanceOf(CustomException.class)
					.extracting(PostServiceTest::errorCodeOf)
					.isEqualTo(PostErrorCode.POST_404);
		}
	}

	@Nested
	@DisplayName("검색 정렬 지표 배치")
	class SyncSearchMetrics {

		@Test
		@DisplayName("조회수를 먼저 반영하고 그 다음 rank_score 를 재계산한다 — 순서가 바뀌면 이번 주기 조회가 점수에 안 들어간다")
		void applySearchMetrics_appliesViewCountsBeforeRecalculatingRankScore() {
			// Map.of() 는 순서를 보장하지 않으므로 한 건만 둔다 — 보고 싶은 건 여러 post 간
			// 순서가 아니라 '조회수 반영 → rank_score 재계산' 이라는 단계 순서다.
			// Redis peek/clear 는 스케줄러가 호출하므로(PostSearchMetricsScheduler) 이
			// 메서드는 이미 읽어온 delta 맵만 받는다.
			postService.applySearchMetrics(Map.of(POST_ID, 3L));

			InOrder order = Mockito.inOrder(postRepository);
			order.verify(postRepository).incrementViewCount(POST_ID, 3L);
			order.verify(postRepository).recalculateRankScores(
					RankScorePolicy.SAVE_WEIGHT,
					RankScorePolicy.LIKE_WEIGHT,
					RankScorePolicy.VIEW_WEIGHT,
					RankScorePolicy.POPULARITY_WEIGHT,
					RankScorePolicy.FRESHNESS_WEIGHT,
					RankScorePolicy.FRESHNESS_HALF_LIFE_DAYS);
		}

		@Test
		@DisplayName("delta 가 비어 있어도 rank_score 재계산은 돈다 — 신선도만으로도 값이 바뀐다")
		void applySearchMetrics_emptyDeltas_stillRecalculates() {
			postService.applySearchMetrics(Map.of());

			verify(postRepository, never()).incrementViewCount(any(), anyLong());
			verify(postRepository).recalculateRankScores(
					anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
		}
	}

	private User givenAuthor() {
		User author = author();
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(author));
		return author;
	}

	private Post givenSavedPost(User author) {
		Post post = Post.publish(author, LocalDate.now(), AspectRatio.RATIO_3_4, false);
		ReflectionTestUtils.setField(post, "id", POST_ID);
		given(postRepository.save(any(Post.class))).willReturn(post);
		given(postImageRepository.save(any(PostImage.class)))
				.willAnswer(invocation -> invocation.getArgument(0));
		return post;
	}

	private Post givenExistingPost(User author) {
		Post post = Post.publish(author, LocalDate.now(), AspectRatio.RATIO_3_4, false);
		ReflectionTestUtils.setField(post, "id", POST_ID);
		given(postRepository.findActive(POST_ID)).willReturn(Optional.of(post));
		return post;
	}

	private PostItemTag givenExistingTag(Post post, Item item) {
		PostImage image = PostImage.of(post, "post/2026/09/a.jpg", 0, true);
		ReflectionTestUtils.setField(image, "id", 500L);
		PostItemTag tag = PostItemTag.of(image, item, new BigDecimal("0.4200"), new BigDecimal("0.6100"));
		ReflectionTestUtils.setField(tag, "id", TAG_ID);
		given(postItemTagRepository.findAllByPostId(POST_ID)).willReturn(List.of(tag));
		return tag;
	}

	private ItemOwnership givenOwnedItem() {
		User owner = author();
		Item item = item(owner);
		ReflectionTestUtils.setField(item, "id", ITEM_ID);
		ItemOwnership ownership = ownership(item, owner);
		given(itemRepository.findAllByIds(any())).willReturn(List.of(item));
		given(itemOwnershipRepository.findCurrentByItemIds(any())).willReturn(List.of(ownership));
		given(itemWearRecordRepository.save(any(ItemWearRecord.class)))
				.willAnswer(invocation -> invocation.getArgument(0));
		return ownership;
	}

	private static User author() {
		User user = User.signUp("gildong@naver.com", "$2a$10$hashed", "홍길동",
				"821012345678", "Sujun", null, Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", USER_ID);
		return user;
	}

	private static User otherUser() {
		User user = User.signUp("yujin@naver.com", "$2a$10$hashed", "김유진",
				"821099998888", "YuJin", null, Gender.FEMALE, 165, 50);
		ReflectionTestUtils.setField(user, "id", OTHER_USER_ID);
		return user;
	}

	private static Item item(User owner) {
		return Item.register(owner, null, "LVC 1955 501", "Levis", "L",
				WearTarget.UNISEX, "item/2026/09/a.jpg", LocalDate.now().minusYears(1));
	}

	private static ItemOwnership ownership(Item item, User owner) {
		return ItemOwnership.openInitial(item, owner, LocalDate.now().minusYears(1));
	}

	private static PostCreateRequest createRequest() {
		return PostCreateRequest.builder()
				.wornDate(LocalDate.now())
				.aspectRatio(AspectRatio.RATIO_3_4)
				.images(List.of(imageRequest("post/2026/09/a.jpg", 0, true, List.of(tagRequest(ITEM_ID)))))
				.build();
	}

	private static PostImageRequest imageRequest(
			String key, int sortOrder, Boolean isCover, List<PostTagRequest> tags
	) {
		return PostImageRequest.builder()
				.imageKey(key)
				.sortOrder(sortOrder)
				.isCover(isCover)
				.tags(tags)
				.build();
	}

	private static PostTagRequest tagRequest(Long itemId) {
		return PostTagRequest.builder()
				.itemId(itemId)
				.posX(new BigDecimal("0.4200"))
				.posY(new BigDecimal("0.6100"))
				.build();
	}

	private static PostUpdateRequest updateRequest(Long afterItemId) {
		return PostUpdateRequest.builder()
				.tags(List.of(PostTagEditRequest.builder()
						.postItemTagId(TAG_ID)
						.itemId(afterItemId)
						.build()))
				.build();
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
