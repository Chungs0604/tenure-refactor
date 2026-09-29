package com.tenure.domain.post.controller;

import com.tenure.domain.post.dto.request.PostCreateRequest;
import com.tenure.domain.post.dto.request.PostCreateRequest.PostImageRequest;
import com.tenure.domain.post.dto.request.PostCreateRequest.PostTagRequest;
import com.tenure.domain.post.dto.request.PostUpdateRequest;
import com.tenure.domain.post.dto.request.PostUpdateRequest.PostTagEditRequest;
import com.tenure.domain.post.dto.response.PostAuthorResponse;
import com.tenure.domain.post.dto.response.PostCreateResponse;
import com.tenure.domain.post.dto.response.PostIdResponse;
import com.tenure.domain.post.dto.response.PostImageResponse;
import com.tenure.domain.post.dto.response.PostLikeResponse;
import com.tenure.domain.post.dto.response.PostResponse;
import com.tenure.domain.post.dto.response.PostSaveResponse;
import com.tenure.domain.post.dto.response.PostSummaryResponse;
import com.tenure.domain.post.dto.response.PostTagResponse;
import com.tenure.domain.listing.enums.SaleStatus;
import com.tenure.domain.post.enums.AspectRatio;
import com.tenure.domain.post.enums.FeedTab;
import com.tenure.domain.post.exception.PostErrorCode;
import com.tenure.domain.post.service.PostQueryService;
import com.tenure.domain.post.service.PostService;
import com.tenure.global.exception.CustomException;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorCodec;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.security.AuthUser;
import com.tenure.support.ControllerSliceTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@Import(CursorCodec.class)
@DisplayName("PostController")
class PostControllerTest extends ControllerSliceTestSupport {

	private static final String POSTS_URL = "/api/v1/posts";
	private static final Long USER_ID = 1L;
	private static final Long POST_ID = 10L;
	private static final Long ITEM_ID = 100L;

	@MockitoBean private PostService postService;
	@MockitoBean private PostQueryService postQueryService;

	@Nested
	@DisplayName("GET /api/v1/posts")
	class GetFeed {

		@Test
		@DisplayName("피드를 커서 형식으로 내려준다")
		void getFeed_authenticated_returnsCursorPage() throws Exception {
			// 2건을 읽고 1건만 내보내는 상황 — 다음 페이지가 있다
			CursorSlice<PostSummaryResponse> slice = CursorSlice.of(
					List.of(summary(), summary()), 1,
					card -> Cursor.of(LocalDate.of(2026, 6, 21).atStartOfDay(), POST_ID));
			given(postQueryService.getFeed(USER_ID, FeedTab.ALL, null, 20)).willReturn(slice);

			mvc.perform(get(POSTS_URL).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content.length()").value(1))
					.andExpect(jsonPath("$.data.content[0].id").value(POST_ID))
					.andExpect(jsonPath("$.data.content[0].isLiked").value(true))
					.andExpect(jsonPath("$.data.content[0].aspectRatio").value("RATIO_3_4"))
					// 카드에는 작성자도 카운트도 그리지 않는다
					.andExpect(jsonPath("$.data.content[0].author").doesNotExist())
					.andExpect(jsonPath("$.data.content[0].likeCount").doesNotExist())
					// 커서는 불투명 문자열로 나간다 — 정렬 키가 그대로 보이지 않는다
					.andExpect(jsonPath("$.data.nextCursor").isString())
					.andExpect(jsonPath("$.data.nextCursor").value(org.hamcrest.Matchers.not(
							org.hamcrest.Matchers.containsString("2026-06-21"))))
					.andExpect(jsonPath("$.data.hasNext").value(true))
					// 페이지 번호는 내려주지 않는다 — 무한 스크롤에 쓸 데가 없다
					.andExpect(jsonPath("$.data.page").doesNotExist());
		}

		@Test
		@DisplayName("마지막 페이지면 커서가 null 이다")
		void getFeed_lastPage_hasNullCursor() throws Exception {
			given(postQueryService.getFeed(USER_ID, FeedTab.ALL, null, 20))
					.willReturn(CursorSlice.empty());

			mvc.perform(get(POSTS_URL).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.nextCursor").doesNotExist())
					.andExpect(jsonPath("$.data.hasNext").value(false));
		}

		@Test
		@DisplayName("받은 커서를 해독해 서비스에 값으로 넘긴다")
		void getFeed_withCursor_decodesBeforeService() throws Exception {
			Cursor cursor = Cursor.of(LocalDate.of(2026, 6, 21).atStartOfDay(), 99L);
			String encoded = new CursorCodec().encode(cursor);
			given(postQueryService.getFeed(USER_ID, FeedTab.FOLLOWING, cursor, 5))
					.willReturn(CursorSlice.empty());

			mvc.perform(get(POSTS_URL)
							.param("tab", "FOLLOWING")
							.param("cursor", encoded)
							.param("size", "5")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk());

			verify(postQueryService).getFeed(USER_ID, FeedTab.FOLLOWING, cursor, 5);
		}

		@Test
		@DisplayName("깨진 커서는 400 이다 — 조용히 첫 페이지를 주면 같은 목록이 반복해 붙는다")
		void getFeed_malformedCursor_returnsBadRequest() throws Exception {
			mvc.perform(get(POSTS_URL).param("cursor", "!!broken!!")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON_400"));

			verifyNoInteractions(postQueryService);
		}

		@Test
		@DisplayName("size 가 상한을 넘으면 400 이다 — 조용히 깎지 않는다")
		void getFeed_hugeSize_returnsBadRequest() throws Exception {
			mvc.perform(get(POSTS_URL).param("size", "101")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON_400"));

			verifyNoInteractions(postQueryService);
		}

		@Test
		@DisplayName("없는 탭 값이면 400 이다")
		void getFeed_unknownTab_returnsBadRequest() throws Exception {
			mvc.perform(get(POSTS_URL).param("tab", "POPULAR")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON_400"));

			verifyNoInteractions(postQueryService);
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void getFeed_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(get(POSTS_URL))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("COMMON_401"));

			verifyNoInteractions(postQueryService);
		}
	}

	@Nested
	@DisplayName("GET /api/v1/posts/{postId}")
	class GetPost {

		@Test
		@DisplayName("사진·태그·작성자를 한 응답에 담는다")
		void getPost_exists_returnsDetail() throws Exception {
			given(postQueryService.getPost(USER_ID, POST_ID)).willReturn(detail());

			mvc.perform(get(POSTS_URL + "/" + POST_ID).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.wornDate").value("2026-06-21"))
					.andExpect(jsonPath("$.data.images[0].tags[0].itemName").value("LVC 1955 501"))
					// 판매 중일 때만 말풍선이 2행이 된다 (DEC-102)
					.andExpect(jsonPath("$.data.images[0].tags[0].saleStatus").value("ON_SALE"))
					.andExpect(jsonPath("$.data.images[0].tags[0].postItemTagId").value(1000))
					.andExpect(jsonPath("$.data.author.followerCount").value(101));
		}

		@Test
		@DisplayName("삭제된 게시물이면 404 와 POST_404 다")
		void getPost_deleted_returnsNotFound() throws Exception {
			willThrow(new CustomException(PostErrorCode.POST_404))
					.given(postQueryService).getPost(USER_ID, POST_ID);

			mvc.perform(get(POSTS_URL + "/" + POST_ID).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("POST_404"));
		}
	}

	@Nested
	@DisplayName("POST /api/v1/posts")
	class Create {

		@Test
		@DisplayName("게시하면 id 와 승격 여부를 돌려준다")
		void create_validRequest_returnsIdAndPromotion() throws Exception {
			given(postService.create(eq(USER_ID), any(PostCreateRequest.class)))
					.willReturn(PostCreateResponse.builder().id(POST_ID).gradePromoted(true).build());

			mvc.perform(post(POSTS_URL)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(createRequest())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id").value(POST_ID))
					.andExpect(jsonPath("$.data.gradePromoted").value(true));
		}

		@Test
		@DisplayName("사진이 없으면 400 이고 서비스를 부르지 않는다")
		void create_noImage_returnsBadRequest() throws Exception {
			PostCreateRequest request = PostCreateRequest.builder()
					.wornDate(LocalDate.of(2026, 6, 21))
					.aspectRatio(AspectRatio.RATIO_3_4)
					.images(List.of())
					.build();

			mvc.perform(post(POSTS_URL)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(request)))
					.andExpect(status().isBadRequest());

			verifyNoInteractions(postService);
		}

		@Test
		@DisplayName("미래 착용일은 400 이다 — 아직 입지 않은 옷은 기록이 아니다")
		void create_futureWornDate_returnsBadRequest() throws Exception {
			PostCreateRequest request = PostCreateRequest.builder()
					.wornDate(LocalDate.now().plusDays(1))
					.aspectRatio(AspectRatio.RATIO_3_4)
					.images(List.of(imageRequest()))
					.build();

			mvc.perform(post(POSTS_URL)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(request)))
					.andExpect(status().isBadRequest());

			verifyNoInteractions(postService);
		}

		@Test
		@DisplayName("태그 상한을 넘기면 서비스가 POST_400_TAG_LIMIT 으로 끊는다")
		void create_tooManyTags_returnsTagLimit() throws Exception {
			willThrow(new CustomException(PostErrorCode.POST_400_TAG_LIMIT))
					.given(postService).create(eq(USER_ID), any(PostCreateRequest.class));

			mvc.perform(post(POSTS_URL)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(createRequest())))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("POST_400_TAG_LIMIT"));
		}
	}

	@Nested
	@DisplayName("PATCH · DELETE /api/v1/posts/{postId}")
	class UpdateAndDelete {

		@Test
		@DisplayName("태그를 수정하면 id 를 돌려준다")
		void updateTags_validRequest_returnsId() throws Exception {
			given(postService.updateTags(eq(USER_ID), eq(POST_ID), any(PostUpdateRequest.class)))
					.willReturn(PostIdResponse.builder().id(POST_ID).build());

			mvc.perform(patch(POSTS_URL + "/" + POST_ID)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(updateRequest())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id").value(POST_ID));
		}

		@Test
		@DisplayName("남의 게시물을 고치면 403 이다")
		void updateTags_notAuthor_returnsForbidden() throws Exception {
			willThrow(new CustomException(PostErrorCode.POST_403))
					.given(postService).updateTags(eq(USER_ID), eq(POST_ID), any(PostUpdateRequest.class));

			mvc.perform(patch(POSTS_URL + "/" + POST_ID)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(updateRequest())))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("POST_403"));
		}

		@Test
		@DisplayName("삭제 성공이면 data 가 null 이다")
		void delete_ownPost_returnsNullData() throws Exception {
			mvc.perform(delete(POSTS_URL + "/" + POST_ID).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data").doesNotExist());

			verify(postService).delete(USER_ID, POST_ID);
		}

		@Test
		@DisplayName("판매 중 대표 OOTD 면 409 다")
		void delete_featured_returnsConflict() throws Exception {
			willThrow(new CustomException(PostErrorCode.POST_409_FEATURED))
					.given(postService).delete(USER_ID, POST_ID);

			mvc.perform(delete(POSTS_URL + "/" + POST_ID).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("POST_409_FEATURED"));
		}
	}

	@Nested
	@DisplayName("좋아요 · 저장")
	class Reactions {

		@Test
		@DisplayName("좋아요하면 갱신된 수를 돌려준다")
		void like_firstTime_returnsCount() throws Exception {
			given(postService.like(USER_ID, POST_ID))
					.willReturn(PostLikeResponse.builder().likeCount(1235).build());

			mvc.perform(post(POSTS_URL + "/" + POST_ID + "/like")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.likeCount").value(1235));
		}

		@Test
		@DisplayName("이미 좋아요했으면 409 와 POST_409_LIKE 다")
		void like_alreadyLiked_returnsConflict() throws Exception {
			willThrow(new CustomException(PostErrorCode.POST_409_LIKE))
					.given(postService).like(USER_ID, POST_ID);

			mvc.perform(post(POSTS_URL + "/" + POST_ID + "/like")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("POST_409_LIKE"));
		}

		@Test
		@DisplayName("좋아요 취소는 DELETE 다")
		void unlike_liked_returnsCount() throws Exception {
			given(postService.unlike(USER_ID, POST_ID))
					.willReturn(PostLikeResponse.builder().likeCount(1234).build());

			mvc.perform(delete(POSTS_URL + "/" + POST_ID + "/like")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.likeCount").value(1234));
		}

		@Test
		@DisplayName("저장하면 갱신된 저장 수를 돌려준다")
		void save_firstTime_returnsCount() throws Exception {
			given(postService.save(USER_ID, POST_ID))
					.willReturn(PostSaveResponse.builder().saveCount(7).build());

			mvc.perform(post(POSTS_URL + "/" + POST_ID + "/save")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.saveCount").value(7));
		}

		@Test
		@DisplayName("저장 취소는 DELETE 다")
		void unsave_saved_returnsCount() throws Exception {
			given(postService.unsave(USER_ID, POST_ID))
					.willReturn(PostSaveResponse.builder().saveCount(6).build());

			mvc.perform(delete(POSTS_URL + "/" + POST_ID + "/save")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.saveCount").value(6));
		}

		@Test
		@DisplayName("토큰이 없으면 좋아요도 401 이다")
		void like_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(post(POSTS_URL + "/" + POST_ID + "/like"))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(postService);
		}
	}

	private static UsernamePasswordAuthenticationToken principalOf(Long userId) {
		return new UsernamePasswordAuthenticationToken(new AuthUser(userId), null, List.of());
	}

	private static PostSummaryResponse summary() {
		return PostSummaryResponse.builder()
				.id(POST_ID)
				.imageUrl("/uploads/post/2026/09/a.jpg")
				.aspectRatio(AspectRatio.RATIO_3_4)
				.isLiked(true)
				.isSaved(false)
				.build();
	}

	private static PostResponse detail() {
		return PostResponse.builder()
				.id(POST_ID)
				.wornDate(LocalDate.of(2026, 6, 21))
				.aspectRatio(AspectRatio.RATIO_3_4)
				.images(List.of(PostImageResponse.builder()
						.id(500L)
						.url("/uploads/post/2026/09/a.jpg")
						.isCover(true)
						.tags(List.of(PostTagResponse.builder()
								.postItemTagId(1000L)
								.itemId(ITEM_ID)
								.itemName("LVC 1955 501")
								.brandName("Levis")
								.saleStatus(SaleStatus.ON_SALE)
								.posX(new BigDecimal("0.4200"))
								.posY(new BigDecimal("0.6100"))
								.build()))
						.build()))
				.author(PostAuthorResponse.builder()
						.id(USER_ID)
						.nickname("Sujun")
						.profileImageUrl(null)
						.followerCount(101)
						.postCount(60)
						.build())
				.isFollowing(false)
				.isLiked(true)
				.isSaved(false)
				.likeCount(1234)
				.saveCount(12)
				.build();
	}

	private static PostCreateRequest createRequest() {
		return PostCreateRequest.builder()
				.wornDate(LocalDate.of(2026, 6, 21))
				.aspectRatio(AspectRatio.RATIO_3_4)
				.quickPosted(false)
				.images(List.of(imageRequest()))
				.build();
	}

	private static PostImageRequest imageRequest() {
		return PostImageRequest.builder()
				.imageKey("post/2026/09/a.jpg")
				.sortOrder(0)
				.isCover(true)
				.tags(List.of(PostTagRequest.builder()
						.itemId(ITEM_ID)
						.posX(new BigDecimal("0.4200"))
						.posY(new BigDecimal("0.6100"))
						.build()))
				.build();
	}

	private static PostUpdateRequest updateRequest() {
		return PostUpdateRequest.builder()
				.tags(List.of(PostTagEditRequest.builder()
						.postItemTagId(1000L)
						.itemId(ITEM_ID)
						.build()))
				.build();
	}
}
