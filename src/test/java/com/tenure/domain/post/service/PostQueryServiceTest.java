package com.tenure.domain.post.service;

import com.tenure.domain.block.repository.UserBlockRepository;
import com.tenure.domain.follow.repository.FollowRepository;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.post.dto.response.MyPostSummaryResponse;
import com.tenure.domain.post.dto.response.PostResponse;
import com.tenure.domain.post.dto.response.PostSummaryResponse;
import com.tenure.domain.post.dto.response.TaggedItemResponse;
import com.tenure.domain.post.dto.response.UserPostSummaryResponse;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.entity.PostImage;
import com.tenure.domain.post.entity.PostItemTag;
import com.tenure.domain.post.enums.AspectRatio;
import com.tenure.domain.post.enums.FeedTab;
import com.tenure.domain.post.enums.MyPostTab;
import com.tenure.domain.post.enums.TaggedItemStatus;
import com.tenure.domain.post.exception.PostErrorCode;
import com.tenure.domain.post.mapper.PostMapper;
import com.tenure.domain.post.mapper.PostMapperImpl;
import com.tenure.domain.post.repository.PostImageRepository;
import com.tenure.domain.post.repository.PostItemTagRepository;
import com.tenure.domain.post.repository.PostLikeRepository;
import com.tenure.domain.post.repository.PostRepository;
import com.tenure.domain.post.repository.PostSaveRepository;
import com.tenure.domain.search.enums.RecentViewTargetType;
import com.tenure.domain.post.store.PostViewCountBuffer;
import com.tenure.domain.search.store.RecentViewStore;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.domain.wishlist.repository.WishlistRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.storage.FileStorage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PostQueryService")
class PostQueryServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long OTHER_USER_ID = 2L;
	private static final Long POST_ID = 10L;
	private static final Long IMAGE_ID = 500L;
	private static final Long ITEM_ID = 100L;
	private static final Long OTHER_ITEM_ID = 200L;
	private static final Long LISTING_ID = 300L;
	private static final int SIZE = 20;

	@Mock private PostRepository postRepository;
	@Mock private PostImageRepository postImageRepository;
	@Mock private PostItemTagRepository postItemTagRepository;
	@Mock private PostLikeRepository postLikeRepository;
	@Mock private PostSaveRepository postSaveRepository;
	@Mock private ItemListingRepository itemListingRepository;
	@Mock private WishlistRepository wishlistRepository;
	@Mock private FollowRepository followRepository;
	@Mock private UserBlockRepository userBlockRepository;
	@Mock private UserRepository userRepository;
	@Mock private FileStorage fileStorage;
	@Mock private RecentViewStore recentViewStore;
	@Mock private PostViewCountBuffer postViewCountBuffer;

	/** mock 하지 않는다 — mock 하면 응답에 값을 안 실어도 테스트가 통과한다. */
	@Spy private PostMapper postMapper = new PostMapperImpl();

	@InjectMocks private PostQueryService postQueryService;

	@Nested
	@DisplayName("홈 피드")
	class GetFeed {

		@Test
		@DisplayName("대표 사진과 좋아요·저장 여부를 채워 내려준다")
		void getFeed_hasPosts_fillsCoverAndFlags() {
			givenUrlPrefix();
			Post post = post(author());
			given(postRepository.findFeed(USER_ID, FeedTab.ALL, null, SIZE))
					.willReturn(List.of(post));
			given(postImageRepository.findCovers(List.of(POST_ID)))
					.willReturn(List.of(cover(post)));
			given(postLikeRepository.findLikedPostIds(USER_ID, List.of(POST_ID)))
					.willReturn(List.of(POST_ID));
			given(postSaveRepository.findSavedPostIds(USER_ID, List.of(POST_ID)))
					.willReturn(List.of());

			CursorSlice<PostSummaryResponse> slice =
					postQueryService.getFeed(USER_ID, FeedTab.ALL, null, SIZE);

			PostSummaryResponse card = slice.getContent().get(0);
			assertThat(card.getId()).isEqualTo(POST_ID);
			assertThat(card.getImageUrl()).isEqualTo("/uploads/post/2026/09/a.jpg");
			assertThat(card.getAspectRatio()).isEqualTo(AspectRatio.RATIO_3_4);
			assertThat(card.getIsLiked()).isTrue();
			assertThat(card.getIsSaved()).isFalse();
		}

		@Test
		@DisplayName("결과가 없으면 대표 사진·반응 조회를 하지 않는다 — 빈 IN 절을 만들지 않는다")
		void getFeed_empty_skipsExtraQueries() {
			given(postRepository.findFeed(USER_ID, FeedTab.FOLLOWING, null, SIZE))
					.willReturn(List.of());

			CursorSlice<PostSummaryResponse> slice =
					postQueryService.getFeed(USER_ID, FeedTab.FOLLOWING, null, SIZE);

			assertThat(slice.getContent()).isEmpty();
			assertThat(slice.getHasNext()).isFalse();
			assertThat(slice.getNextCursor()).isNull();
			verifyNoInteractions(postImageRepository, postLikeRepository, postSaveRepository);
		}

		@Test
		@DisplayName("마지막 페이지면 커서를 주지 않는다")
		void getFeed_lastPage_hasNoCursor() {
			givenUrlPrefix();
			Post post = post(author());
			given(postRepository.findFeed(USER_ID, FeedTab.ALL, null, 1)).willReturn(List.of(post));
			givenCoverAndFlags(post);

			CursorSlice<PostSummaryResponse> slice =
					postQueryService.getFeed(USER_ID, FeedTab.ALL, null, 1);

			assertThat(slice.getHasNext()).isFalse();
			assertThat(slice.getNextCursor()).isNull();
		}

		@Test
		@DisplayName("한 건 더 읽히면 그 건은 빼고 커서를 준다 — 여분 한 건이 다음 페이지 유무를 알려준다")
		void getFeed_moreRows_trimsAndIssuesCursor() {
			givenUrlPrefix();
			Post first = post(author());
			Post second = post(author());
			ReflectionTestUtils.setField(second, "id", 11L);
			given(postRepository.findFeed(USER_ID, FeedTab.ALL, null, 1))
					.willReturn(List.of(first, second));
			givenCoverAndFlags(first);

			CursorSlice<PostSummaryResponse> slice =
					postQueryService.getFeed(USER_ID, FeedTab.ALL, null, 1);

			assertThat(slice.getContent()).hasSize(1);
			assertThat(slice.getHasNext()).isTrue();
			// 커서는 화면에 나간 마지막 행으로 만든다. 잘라낸 행으로 만들면 그 한 건이 영영 안 보인다
			assertThat(slice.getNextCursor().getId()).isEqualTo(POST_ID);
			assertThat(slice.getNextCursor().asDateTime()).isEqualTo(first.getCreatedAt());
		}

		@Test
		@DisplayName("받은 커서를 쿼리에 그대로 넘긴다 — 해독은 전송 계층의 몫이다")
		void getFeed_withCursor_passesCursorToQuery() {
			Cursor cursor = Cursor.of(LocalDate.of(2026, 6, 21).atStartOfDay(), 99L);
			given(postRepository.findFeed(USER_ID, FeedTab.ALL, cursor, SIZE)).willReturn(List.of());

			postQueryService.getFeed(USER_ID, FeedTab.ALL, cursor, SIZE);

			verify(postRepository).findFeed(USER_ID, FeedTab.ALL, cursor, SIZE);
		}

		@Test
		@DisplayName("탭을 그대로 넘긴다 — 조건은 쿼리가 맡는다")
		void getFeed_followingTab_passesTabToQuery() {
			given(postRepository.findFeed(USER_ID, FeedTab.FOLLOWING, null, SIZE))
					.willReturn(List.of());

			postQueryService.getFeed(USER_ID, FeedTab.FOLLOWING, null, SIZE);

			verify(postRepository).findFeed(USER_ID, FeedTab.FOLLOWING, null, SIZE);
		}

		private void givenCoverAndFlags(Post post) {
			given(postImageRepository.findCovers(List.of(post.getId())))
					.willReturn(List.of(cover(post)));
			given(postLikeRepository.findLikedPostIds(USER_ID, List.of(post.getId())))
					.willReturn(List.of());
			given(postSaveRepository.findSavedPostIds(USER_ID, List.of(post.getId())))
					.willReturn(List.of());
		}
	}

	@Nested
	@DisplayName("OOTD 상세")
	class GetPost {

		@Test
		@DisplayName("사진별로 태그를 묶고 아이템의 판매 상태를 그대로 싣는다")
		void getPost_hasTags_groupsTagsAndFillsSaleStatus() {
			givenUrlPrefix();
			User author = author();
			Post post = post(author);
			PostImage image = cover(post);

			Item onSaleItem = item(author, ITEM_ID, "LVC 1955 501");
			Item plainItem = item(author, OTHER_ITEM_ID, "Third Cut");

			// mock 을 stubbing 하는 중에 다른 mock 을 만들 수 없다 — 먼저 만들어 둔다
			onSaleItem.changeSaleStatus(SaleStatus.ON_SALE);

			given(postRepository.findActiveWithAuthor(POST_ID)).willReturn(Optional.of(post));
			given(postItemTagRepository.findAllByPostId(POST_ID))
					.willReturn(List.of(tag(image, onSaleItem), tag(image, plainItem)));
			given(postImageRepository.findAllByPostId(POST_ID)).willReturn(List.of(image));

			PostResponse response = postQueryService.getPost(USER_ID, POST_ID);

			assertThat(response.getImages()).hasSize(1);
			assertThat(response.getImages().get(0).getTags()).hasSize(2);
			assertThat(response.getImages().get(0).getTags().get(0).getSaleStatus())
					.isEqualTo(SaleStatus.ON_SALE);
			assertThat(response.getImages().get(0).getTags().get(0).getItemName())
					.isEqualTo("LVC 1955 501");
			// 미판매면 말풍선이 1행으로 접힌다 — '#판매중' 은 ON_SALE 일 때만 붙는다 (DEC-102)
			assertThat(response.getImages().get(0).getTags().get(1).getSaleStatus())
					.isEqualTo(SaleStatus.NOT_FOR_SALE);
		}

		@Test
		@DisplayName("본인 게시물이면 팔로우 여부를 묻지 않는다")
		void getPost_ownPost_doesNotCheckFollow() {
			givenUrlPrefix();
			Post post = post(author());
			given(postRepository.findActiveWithAuthor(POST_ID)).willReturn(Optional.of(post));
			given(postItemTagRepository.findAllByPostId(POST_ID)).willReturn(List.of());
			given(postImageRepository.findAllByPostId(POST_ID)).willReturn(List.of(cover(post)));

			PostResponse response = postQueryService.getPost(USER_ID, POST_ID);

			assertThat(response.getIsFollowing()).isFalse();
			verifyNoInteractions(followRepository);
		}

		@Test
		@DisplayName("남의 게시물이면 팔로우 여부를 함께 준다")
		void getPost_othersPost_fillsFollowing() {
			givenUrlPrefix();
			Post post = post(otherUser());
			given(postRepository.findActiveWithAuthor(POST_ID)).willReturn(Optional.of(post));
			given(postItemTagRepository.findAllByPostId(POST_ID)).willReturn(List.of());
			given(postImageRepository.findAllByPostId(POST_ID)).willReturn(List.of(cover(post)));
			given(followRepository.existsFollow(USER_ID, OTHER_USER_ID)).willReturn(true);

			assertThat(postQueryService.getPost(USER_ID, POST_ID).getIsFollowing()).isTrue();
		}

		@Test
		@DisplayName("조회하면 최근 열람에 남긴다 — 검색 화면의 추천 입력이다")
		void getPost_always_recordsRecentView() {
			givenUrlPrefix();
			Post post = post(author());
			given(postRepository.findActiveWithAuthor(POST_ID)).willReturn(Optional.of(post));
			given(postItemTagRepository.findAllByPostId(POST_ID)).willReturn(List.of());
			given(postImageRepository.findAllByPostId(POST_ID)).willReturn(List.of());

			postQueryService.getPost(USER_ID, POST_ID);

			verify(recentViewStore).add(USER_ID, RecentViewTargetType.POST, POST_ID);
		}

		@Test
		@DisplayName("삭제된 게시물이면 POST_404 다")
		void getPost_deleted_throwsNotFound() {
			given(postRepository.findActiveWithAuthor(POST_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> postQueryService.getPost(USER_ID, POST_ID))
					.isInstanceOf(CustomException.class)
					.extracting(PostQueryServiceTest::errorCodeOf)
					.isEqualTo(PostErrorCode.POST_404);

			verifyNoInteractions(recentViewStore);
		}
	}

	@Nested
	@DisplayName("태그된 아이템 목록")
	class GetTaggedItems {

		@Test
		@DisplayName("판매 중이면 상태와 가격을 싣고 관심 여부를 채운다")
		void getTaggedItems_onSale_fillsPriceAndWish() {
			givenUrlPrefix();
			User author = author();
			Post post = post(author);
			PostImage image = cover(post);
			Item onSaleItem = item(author, ITEM_ID, "LVC 1955 501");
			// 구매 제안 허용은 판매 등록이 아니라 아이템이 갖는다 (DEC-024)
			ReflectionTestUtils.setField(onSaleItem, "offerAllowed", true);

			onSaleItem.changeSaleStatus(SaleStatus.ON_SALE);
			ItemListing onSaleListing = listing(onSaleItem);

			given(postImageRepository.findActiveWithPost(IMAGE_ID)).willReturn(Optional.of(image));
			given(postItemTagRepository.findAllByPostImageId(IMAGE_ID))
					.willReturn(List.of(tag(image, onSaleItem)));
			given(itemListingRepository.findOpenByItemIds(List.of(ITEM_ID)))
					.willReturn(List.of(onSaleListing));
			given(wishlistRepository.findAlarmEnabledItemIds(USER_ID, List.of(ITEM_ID)))
					.willReturn(List.of(ITEM_ID));

			List<TaggedItemResponse> items = postQueryService.getTaggedItems(USER_ID, IMAGE_ID);

			TaggedItemResponse item = items.get(0);
			assertThat(item.getStatus()).isEqualTo(TaggedItemStatus.ON_SALE);
			assertThat(item.getListingId()).isEqualTo(LISTING_ID);
			assertThat(item.getPrice()).isEqualTo("360000");
			assertThat(item.getOfferAllowed()).isTrue();
			assertThat(item.getAlarmEnabled()).isTrue();
			assertThat(item.getCoverImageUrl()).isEqualTo("/uploads/item/2026/09/a.jpg");
		}

		@Test
		@DisplayName("판매 등록이 없으면 미판매이고 가격이 비어 있다 — CTA 는 offerAllowed 가 정한다")
		void getTaggedItems_neverListed_hasNoListingId() {
			givenUrlPrefix();
			User author = author();
			Post post = post(author);
			PostImage image = cover(post);
			Item plainItem = item(author, ITEM_ID, "Third Cut");

			given(postImageRepository.findActiveWithPost(IMAGE_ID)).willReturn(Optional.of(image));
			given(postItemTagRepository.findAllByPostImageId(IMAGE_ID))
					.willReturn(List.of(tag(image, plainItem)));
			given(itemListingRepository.findOpenByItemIds(List.of(ITEM_ID))).willReturn(List.of());
			given(wishlistRepository.findAlarmEnabledItemIds(USER_ID, List.of(ITEM_ID)))
					.willReturn(List.of());

			TaggedItemResponse item = postQueryService.getTaggedItems(USER_ID, IMAGE_ID).get(0);

			assertThat(item.getStatus()).isEqualTo(TaggedItemStatus.NOT_FOR_SALE);
			assertThat(item.getListingId()).isNull();
			assertThat(item.getPrice()).isNull();
			assertThat(item.getOfferAllowed()).isFalse();
		}

		@Test
		@DisplayName("삭제된 아이템은 DELETED 로 내려간다 — 화면에서 회색 처리된다")
		void getTaggedItems_deletedItem_returnsDeletedStatus() {
			givenUrlPrefix();
			User author = author();
			Post post = post(author);
			PostImage image = cover(post);
			Item deletedItem = item(author, ITEM_ID, "LVC 1955 501");
			ReflectionTestUtils.setField(deletedItem, "deletedAt", java.time.LocalDateTime.now());
			deletedItem.changeSaleStatus(SaleStatus.SOLD);

			given(postImageRepository.findActiveWithPost(IMAGE_ID)).willReturn(Optional.of(image));
			given(postItemTagRepository.findAllByPostImageId(IMAGE_ID))
					.willReturn(List.of(tag(image, deletedItem)));
			given(itemListingRepository.findOpenByItemIds(List.of(ITEM_ID))).willReturn(List.of());
			given(wishlistRepository.findAlarmEnabledItemIds(USER_ID, List.of(ITEM_ID)))
					.willReturn(List.of());

			TaggedItemResponse item = postQueryService.getTaggedItems(USER_ID, IMAGE_ID).get(0);

			assertThat(item.getStatus()).isEqualTo(TaggedItemStatus.DELETED);
		}

		@Test
		@DisplayName("없는 사진이면 POST_404_IMAGE 다")
		void getTaggedItems_unknownImage_throwsNotFound() {
			given(postImageRepository.findActiveWithPost(IMAGE_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> postQueryService.getTaggedItems(USER_ID, IMAGE_ID))
					.isInstanceOf(CustomException.class)
					.extracting(PostQueryServiceTest::errorCodeOf)
					.isEqualTo(PostErrorCode.POST_404_IMAGE);
		}
	}

	@Nested
	@DisplayName("내 게시물 · 타인 게시물")
	class GetLists {

		@Test
		@DisplayName("탭과 월을 그대로 넘기고 반응 여부를 채운다")
		void getMyPosts_savedTab_passesFilters() {
			givenUrlPrefix();
			Post post = post(author());
			YearMonth yearMonth = YearMonth.of(2026, 6);
			given(postRepository.findMyPosts(USER_ID, MyPostTab.SAVED, yearMonth, null, SIZE))
					.willReturn(List.of(post));
			given(postImageRepository.findCovers(List.of(POST_ID))).willReturn(List.of(cover(post)));
			given(postLikeRepository.findLikedPostIds(USER_ID, List.of(POST_ID))).willReturn(List.of());
			given(postSaveRepository.findSavedPostIds(USER_ID, List.of(POST_ID)))
					.willReturn(List.of(POST_ID));

			CursorSlice<MyPostSummaryResponse> slice =
					postQueryService.getMyPosts(USER_ID, MyPostTab.SAVED, yearMonth, null, SIZE);

			MyPostSummaryResponse card = slice.getContent().get(0);
			assertThat(card.getIsSaved()).isTrue();
			assertThat(card.getIsLiked()).isFalse();
			assertThat(card.getWornDate()).isEqualTo(post.getWornDate());
		}

		@Test
		@DisplayName("그리드의 커서 키는 착용일이다 — 정렬과 같은 값이어야 경계가 맞는다")
		void getMyPosts_moreRows_cursorKeyIsWornDate() {
			givenUrlPrefix();
			Post first = post(author());
			Post second = post(author());
			ReflectionTestUtils.setField(second, "id", 11L);
			given(postRepository.findMyPosts(USER_ID, MyPostTab.POSTS, null, null, 1))
					.willReturn(List.of(first, second));
			given(postImageRepository.findCovers(List.of(POST_ID))).willReturn(List.of(cover(first)));
			given(postLikeRepository.findLikedPostIds(USER_ID, List.of(POST_ID))).willReturn(List.of());
			given(postSaveRepository.findSavedPostIds(USER_ID, List.of(POST_ID))).willReturn(List.of());

			CursorSlice<MyPostSummaryResponse> slice =
					postQueryService.getMyPosts(USER_ID, MyPostTab.POSTS, null, null, 1);

			assertThat(slice.getNextCursor().asDate()).isEqualTo(first.getWornDate());
		}

		@Test
		@DisplayName("차단 관계면 빈 목록이다 — 404 로 돌려주면 차단 사실이 드러난다")
		void getUserPosts_blocked_returnsEmpty() {
			given(userRepository.existsById(OTHER_USER_ID)).willReturn(true);
			given(userBlockRepository.existsMutualBlock(USER_ID, OTHER_USER_ID)).willReturn(true);

			CursorSlice<UserPostSummaryResponse> slice =
					postQueryService.getUserPosts(USER_ID, OTHER_USER_ID, null, SIZE);

			assertThat(slice.getContent()).isEmpty();
			verify(postRepository, times(0)).findUserPosts(anyLong(), any(), anyInt());
		}

		@Test
		@DisplayName("나를 차단한 사람의 게시물도 보이지 않는다")
		void getUserPosts_blockedByTarget_returnsEmpty() {
			given(userRepository.existsById(OTHER_USER_ID)).willReturn(true);
			given(userBlockRepository.existsMutualBlock(USER_ID, OTHER_USER_ID)).willReturn(true);

			assertThat(postQueryService.getUserPosts(USER_ID, OTHER_USER_ID, null, SIZE).getContent())
					.isEmpty();
		}

		@Test
		@DisplayName("없는 사용자면 USER_404 다")
		void getUserPosts_unknownUser_throwsNotFound() {
			given(userRepository.existsById(OTHER_USER_ID)).willReturn(false);

			assertThatThrownBy(() -> postQueryService.getUserPosts(USER_ID, OTHER_USER_ID, null, SIZE))
					.isInstanceOf(CustomException.class)
					.extracting(PostQueryServiceTest::errorCodeOf)
					.isEqualTo(UserErrorCode.USER_404);
		}

		@Test
		@DisplayName("차단이 없으면 대표 사진을 채워 내려준다")
		void getUserPosts_notBlocked_fillsCover() {
			givenUrlPrefix();
			Post post = post(otherUser());
			given(userRepository.existsById(OTHER_USER_ID)).willReturn(true);
			given(postRepository.findUserPosts(OTHER_USER_ID, null, SIZE)).willReturn(List.of(post));
			given(postImageRepository.findCovers(List.of(POST_ID))).willReturn(List.of(cover(post)));
			given(postLikeRepository.findLikedPostIds(USER_ID, List.of(POST_ID)))
					.willReturn(List.of(POST_ID));
			given(postSaveRepository.findSavedPostIds(USER_ID, List.of(POST_ID))).willReturn(List.of());

			UserPostSummaryResponse card = postQueryService
					.getUserPosts(USER_ID, OTHER_USER_ID, null, SIZE).getContent().get(0);

			assertThat(card.getImageUrl()).isEqualTo("/uploads/post/2026/09/a.jpg");
			// 남의 게시물에도 하트·북마크 오버레이가 붙는다
			assertThat(card.getIsLiked()).isTrue();
			assertThat(card.getIsSaved()).isFalse();
			assertThat(card.getAspectRatio()).isEqualTo(AspectRatio.RATIO_3_4);
		}
	}

	private void givenUrlPrefix() {
		given(fileStorage.toUrl(anyString()))
				.willAnswer(invocation -> "/uploads/" + invocation.getArgument(0));
	}

	private static User author() {
		User user = User.signUp("gildong@naver.com", "$2a$10$hashed", "홍길동",
				"821012345678", "Sujun", "profile/2026/09/me.jpg", Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", USER_ID);
		return user;
	}

	private static User otherUser() {
		User user = User.signUp("yujin@naver.com", "$2a$10$hashed", "김유진",
				"821099998888", "YuJin", null, Gender.FEMALE, 165, 50);
		ReflectionTestUtils.setField(user, "id", OTHER_USER_ID);
		return user;
	}

	private static Post post(User author) {
		Post post = Post.publish(author, LocalDate.of(2026, 6, 21), AspectRatio.RATIO_3_4, false);
		ReflectionTestUtils.setField(post, "id", POST_ID);
		// 감사 필드는 저장 시 채워진다. 피드 커서가 게시일을 쓰므로 여기서 대신 넣는다
		ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.of(2026, 6, 21, 18, 30));
		return post;
	}

	private static PostImage cover(Post post) {
		PostImage image = PostImage.of(post, "post/2026/09/a.jpg", 0, true);
		ReflectionTestUtils.setField(image, "id", IMAGE_ID);
		return image;
	}

	private static PostItemTag tag(PostImage image, Item item) {
		PostItemTag tag = PostItemTag.of(
				image, item, new BigDecimal("0.4200"), new BigDecimal("0.6100"));
		ReflectionTestUtils.setField(tag, "id", 1000L + item.getId());
		return tag;
	}

	private static Item item(User owner, Long id, String name) {
		Item item = Item.register(owner, null, name, "Levis", "L",
				WearTarget.UNISEX, "item/2026/09/a.jpg", LocalDate.now().minusYears(1));
		ReflectionTestUtils.setField(item, "id", id);
		return item;
	}

	/**
	 * 판매 등록은 mock 으로 만든다 — listing 도메인에 아직 팩토리가 없고, 여기서 검증하는 것은
	 * 게시물 응답이 가격을 어떻게 싣는지다. 판매 상태는 아이템이 들고 있다 (DEC-026).
	 */
	private static ItemListing listing(Item item) {
		ItemListing listing = mock(ItemListing.class);
		given(listing.getId()).willReturn(LISTING_ID);
		given(listing.getItem()).willReturn(item);
		given(listing.getPrice()).willReturn(360000);
		return listing;
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
