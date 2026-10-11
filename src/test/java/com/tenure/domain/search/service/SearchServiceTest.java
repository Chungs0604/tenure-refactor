package com.tenure.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.tenure.domain.follow.repository.FollowRepository;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.entity.PostImage;
import com.tenure.domain.post.enums.AspectRatio;
import com.tenure.domain.post.repository.PostImageRepository;
import com.tenure.domain.search.dto.PostSearchCriteria;
import com.tenure.domain.search.dto.response.RecommendationResponse;
import com.tenure.domain.search.dto.response.SuggestedKeywordResponse;
import com.tenure.domain.search.entity.SearchHistory;
import com.tenure.domain.search.enums.PostSortType;
import com.tenure.domain.search.enums.RecentViewTargetType;
import com.tenure.domain.search.enums.RecommendationSection;
import com.tenure.domain.search.mapper.SearchMapper;
import com.tenure.domain.search.mapper.SearchMapperImpl;
import com.tenure.domain.search.repository.PostSearchRepository;
import com.tenure.domain.search.repository.PostSearchRow;
import com.tenure.domain.search.repository.RecommendationRepository;
import com.tenure.domain.search.repository.SearchHistoryRepository;
import com.tenure.domain.search.repository.UserSearchRepository;
import com.tenure.domain.search.service.SearchService.PostSearchResult;
import com.tenure.domain.search.store.RecentViewStore;
import com.tenure.domain.search.store.SuggestedKeywordCache;
import com.tenure.domain.user.dto.response.UserSummaryResponse;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.mapper.UserMapper;
import com.tenure.domain.user.mapper.UserMapperImpl;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.storage.FileStorage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SearchService")
class SearchServiceTest {

	private static final Long VIEWER_ID = 1L;
	private static final Long POST_ID = 10L;
	private static final int SIZE = 20;

	@Mock private PostSearchRepository postSearchRepository;
	@Mock private UserSearchRepository userSearchRepository;
	@Mock private RecommendationRepository recommendationRepository;
	@Mock private SearchHistoryRepository searchHistoryRepository;
	@Mock private SuggestedKeywordCache suggestedKeywordCache;
	@Mock private RecentViewStore recentViewStore;
	@Mock private PostImageRepository postImageRepository;
	@Mock private FollowRepository followRepository;
	@Mock private UserRepository userRepository;
	@Mock private FileStorage fileStorage;

	/** mock 하지 않는다 — mock 하면 응답에 값을 안 실어도 테스트가 통과한다. */
	@Spy private SearchMapper searchMapper = new SearchMapperImpl();
	@Spy private UserMapper userMapper = new UserMapperImpl();

	@InjectMocks private SearchService searchService;

	@Nested
	@DisplayName("게시물 검색")
	class SearchPosts {

		@Test
		@DisplayName("키워드가 있으면 검색 기록을 남기고, 결과를 대표 사진과 함께 내려준다")
		void searchPosts_withKeyword_recordsHistoryAndReturnsResult() {
			User user = user(VIEWER_ID);
			given(userRepository.findById(VIEWER_ID)).willReturn(Optional.of(user));
			Post post = post(POST_ID);
			PostSearchCriteria criteria = criteria("데님");
			given(postSearchRepository.search(criteria, VIEWER_ID, null, SIZE))
					.willReturn(List.of(new PostSearchRow(post, Cursor.of(LocalDateTime.now(), POST_ID))));
			given(postImageRepository.findCovers(List.of(POST_ID))).willReturn(List.of(cover(post)));
			given(postSearchRepository.countMatches(criteria, VIEWER_ID)).willReturn(5L);
			givenUrlPrefix();

			PostSearchResult result = searchService.searchPosts(VIEWER_ID, criteria, null, SIZE);

			assertThat(result.totalCount()).isEqualTo(5);
			assertThat(result.slice().getContent().get(0).getId()).isEqualTo(POST_ID);
			assertThat(result.slice().getContent().get(0).getImageUrl())
					.isEqualTo("/uploads/post/2026/09/a.jpg");
			verify(searchHistoryRepository).save(org.mockito.ArgumentMatchers.argThat(
					(SearchHistory h) -> h.getKeyword().equals("데님") && h.getUser().equals(user)));
		}

		@Test
		@DisplayName("커서 끝에 닿아 이번 페이지가 비어도 총 건수는 그대로 내려준다 — "
				+ "0으로 떨어지면 화면 상단 '총 N개'가 스크롤 끝에서 갑자기 0으로 바뀐다")
		void searchPosts_emptyPageAtCursorEnd_stillReturnsTotalCount() {
			User user = user(VIEWER_ID);
			given(userRepository.findById(VIEWER_ID)).willReturn(Optional.of(user));
			PostSearchCriteria criteria = criteria("데님");
			given(postSearchRepository.search(criteria, VIEWER_ID, null, SIZE)).willReturn(List.of());
			given(postSearchRepository.countMatches(criteria, VIEWER_ID)).willReturn(5L);

			PostSearchResult result = searchService.searchPosts(VIEWER_ID, criteria, null, SIZE);

			assertThat(result.totalCount()).isEqualTo(5);
			assertThat(result.slice().isEmpty()).isTrue();
			verify(postImageRepository, never()).findCovers(anyList());
		}

		@Test
		@DisplayName("키워드가 비어 있으면 검색 기록을 남기지 않는다")
		void searchPosts_blankKeyword_doesNotRecordHistory() {
			PostSearchCriteria criteria = criteria(null);
			given(postSearchRepository.search(criteria, VIEWER_ID, null, SIZE)).willReturn(List.of());

			searchService.searchPosts(VIEWER_ID, criteria, null, SIZE);

			verifyNoInteractions(userRepository, searchHistoryRepository);
		}

		@Test
		@DisplayName("검색 기록을 남길 사용자가 없으면 USER_404 이고 검색을 하지 않는다")
		void searchPosts_userNotFound_throwsUserNotFound() {
			given(userRepository.findById(VIEWER_ID)).willReturn(Optional.empty());
			PostSearchCriteria criteria = criteria("데님");

			assertThatThrownBy(() -> searchService.searchPosts(VIEWER_ID, criteria, null, SIZE))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(UserErrorCode.USER_404);

			verifyNoInteractions(postSearchRepository);
		}
	}

	@Nested
	@DisplayName("계정 검색")
	class SearchUsers {

		@Test
		@DisplayName("결과가 있으면 팔로우 여부를 채운다")
		void searchUsers_withRows_fillsIsFollowing() {
			given(userRepository.findById(VIEWER_ID)).willReturn(Optional.of(user(VIEWER_ID)));
			User target = user(2L);
			given(userSearchRepository.searchByNickname("su", VIEWER_ID, null, SIZE))
					.willReturn(List.of(target));
			given(followRepository.findFollowingIdsAmong(VIEWER_ID, List.of(2L))).willReturn(List.of(2L));
			givenUrlPrefix();

			CursorSlice<UserSummaryResponse> slice =
					searchService.searchUsers(VIEWER_ID, "su", null, SIZE);

			assertThat(slice.getContent().get(0).getIsFollowing()).isTrue();
		}

		@Test
		@DisplayName("결과가 없으면 팔로우 여부를 조회하지 않는다")
		void searchUsers_empty_skipsFollowingLookup() {
			given(userRepository.findById(VIEWER_ID)).willReturn(Optional.of(user(VIEWER_ID)));
			given(userSearchRepository.searchByNickname("su", VIEWER_ID, null, SIZE)).willReturn(List.of());

			CursorSlice<UserSummaryResponse> slice =
					searchService.searchUsers(VIEWER_ID, "su", null, SIZE);

			assertThat(slice.isEmpty()).isTrue();
			verifyNoInteractions(followRepository);
		}
	}

	@Nested
	@DisplayName("검색 메인 추천")
	class GetRecommendations {

		@Test
		@DisplayName("최근 열람 게시물을 씨드로 4블록을 조립한다")
		void getRecommendations_assemblesFourBlocks() {
			given(recentViewStore.findRecentTargetIds(VIEWER_ID, RecentViewTargetType.POST, 10))
					.willReturn(List.of(100L));
			Post similar = post(1L);
			Post popular = post(2L);
			Post latest = post(3L);
			User popularUser = user(4L);
			given(recommendationRepository.findSimilarToRecent(List.of(100L), VIEWER_ID, null, 8))
					.willReturn(List.of(similar));
			given(recommendationRepository.findPopularStyle(VIEWER_ID, null, 8)).willReturn(List.of(popular));
			given(recommendationRepository.findNewOotd(VIEWER_ID, null, 8)).willReturn(List.of(latest));
			given(recommendationRepository.findPopularUsers(VIEWER_ID, null, 8)).willReturn(List.of(popularUser));
			given(postImageRepository.findCovers(List.of(1L, 2L, 3L)))
					.willReturn(List.of(cover(similar), cover(popular), cover(latest)));
			given(followRepository.findFollowingIdsAmong(VIEWER_ID, List.of(4L))).willReturn(List.of());
			givenUrlPrefix();

			RecommendationResponse response = searchService.getRecommendations(VIEWER_ID);

			assertThat(response.getSimilarPosts()).extracting("id").containsExactly(1L);
			assertThat(response.getPopularPosts()).extracting("id").containsExactly(2L);
			assertThat(response.getLatestPosts()).extracting("id").containsExactly(3L);
			assertThat(response.getPopularUsers()).extracting("id").containsExactly(4L);
			assertThat(response.getPopularUsers().get(0).getIsFollowing()).isFalse();
		}
	}

	@Nested
	@DisplayName("추천 검색어")
	class GetSuggestedKeywords {

		@Test
		@DisplayName("캐시가 있으면 집계하지 않는다")
		void getSuggestedKeywords_cacheHit_doesNotAggregate() {
			given(suggestedKeywordCache.get()).willReturn(List.of("데님", "반팔"));

			List<SuggestedKeywordResponse> result = searchService.getSuggestedKeywords();

			assertThat(result).extracting(SuggestedKeywordResponse::getKeyword)
					.containsExactly("데님", "반팔");
			verifyNoInteractions(searchHistoryRepository);
		}

		@Test
		@DisplayName("캐시가 비어 있으면 집계해서 캐시를 채우고 돌려준다")
		void getSuggestedKeywords_cacheMiss_aggregatesAndCaches() {
			given(suggestedKeywordCache.get()).willReturn(List.of());
			given(searchHistoryRepository.findTrendingKeywords(any(), eq(10))).willReturn(List.of("데님"));

			List<SuggestedKeywordResponse> result = searchService.getSuggestedKeywords();

			assertThat(result).extracting(SuggestedKeywordResponse::getKeyword).containsExactly("데님");
			verify(suggestedKeywordCache).put(List.of("데님"));
		}
	}

	@Nested
	@DisplayName("추천 섹션 더보기")
	class GetRecommendationSection {

		@Test
		@DisplayName("POPULAR_USER 는 사용자 목록이고 커서 키가 follower_count 다")
		void getRecommendationSection_popularUser_usesFollowerCountCursor() {
			User first = userWithFollowers(1L, 20);
			User second = userWithFollowers(2L, 10);
			given(recommendationRepository.findPopularUsers(VIEWER_ID, null, 1))
					.willReturn(List.of(first, second));
			given(followRepository.findFollowingIdsAmong(VIEWER_ID, List.of(1L))).willReturn(List.of());
			givenUrlPrefix();

			CursorSlice<?> slice = searchService.getRecommendationSection(
					VIEWER_ID, RecommendationSection.POPULAR_USER, null, 1);

			assertThat(slice.getHasNext()).isTrue();
			assertThat(slice.getNextCursor().asInt()).isEqualTo(20);
		}

		@Test
		@DisplayName("SIMILAR_TO_RECENT 는 최근 열람을 씨드로 쓰고 커서 키가 rank_score 다")
		void getRecommendationSection_similarToRecent_usesRankScoreCursor() {
			given(recentViewStore.findRecentTargetIds(VIEWER_ID, RecentViewTargetType.POST, 10))
					.willReturn(List.of(100L));
			Post first = postWithRankScore(1L, new BigDecimal("5.0000"));
			Post second = postWithRankScore(2L, new BigDecimal("3.0000"));
			given(recommendationRepository.findSimilarToRecent(List.of(100L), VIEWER_ID, null, 1))
					.willReturn(List.of(first, second));
			given(postImageRepository.findCovers(List.of(1L))).willReturn(List.of(cover(first)));
			givenUrlPrefix();

			CursorSlice<?> slice = searchService.getRecommendationSection(
					VIEWER_ID, RecommendationSection.SIMILAR_TO_RECENT, null, 1);

			assertThat(slice.getNextCursor().asBigDecimal()).isEqualTo(new BigDecimal("5.0000"));
		}

		@Test
		@DisplayName("POPULAR_STYLE 은 커서 키가 like_count 다")
		void getRecommendationSection_popularStyle_usesLikeCountCursor() {
			Post first = postWithLikeCount(1L, 20);
			Post second = postWithLikeCount(2L, 10);
			given(recommendationRepository.findPopularStyle(VIEWER_ID, null, 1))
					.willReturn(List.of(first, second));
			given(postImageRepository.findCovers(List.of(1L))).willReturn(List.of(cover(first)));
			givenUrlPrefix();

			CursorSlice<?> slice = searchService.getRecommendationSection(
					VIEWER_ID, RecommendationSection.POPULAR_STYLE, null, 1);

			assertThat(slice.getNextCursor().asInt()).isEqualTo(20);
		}

		@Test
		@DisplayName("NEW_OOTD 는 커서 키가 created_at 이다")
		void getRecommendationSection_newOotd_usesCreatedAtCursor() {
			LocalDateTime now = LocalDateTime.of(2026, 6, 21, 12, 0);
			Post first = postWithCreatedAt(1L, now);
			Post second = postWithCreatedAt(2L, now.minusDays(1));
			given(recommendationRepository.findNewOotd(VIEWER_ID, null, 1)).willReturn(List.of(first, second));
			given(postImageRepository.findCovers(List.of(1L))).willReturn(List.of(cover(first)));
			givenUrlPrefix();

			CursorSlice<?> slice = searchService.getRecommendationSection(
					VIEWER_ID, RecommendationSection.NEW_OOTD, null, 1);

			assertThat(slice.getNextCursor().asDateTime()).isEqualTo(now);
		}

		@Test
		@DisplayName("결과가 없으면 빈 조각이다")
		void getRecommendationSection_empty_returnsEmptySlice() {
			given(recommendationRepository.findPopularStyle(VIEWER_ID, null, SIZE)).willReturn(List.of());

			CursorSlice<?> slice = searchService.getRecommendationSection(
					VIEWER_ID, RecommendationSection.POPULAR_STYLE, null, SIZE);

			assertThat(slice.isEmpty()).isTrue();
		}
	}

	private void givenUrlPrefix() {
		given(fileStorage.toUrl(anyString())).willAnswer(inv -> "/uploads/" + inv.getArgument(0));
	}

	private PostSearchCriteria criteria(String keyword) {
		return new PostSearchCriteria(keyword, null, null, null, null, null, null, null, PostSortType.LATEST);
	}

	private static User user(Long id) {
		User user = User.signUp("user" + id + "@tenure.app", "$2a$10$hashed", "홍길동",
				"821012345678", "nick" + id, null, Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static User userWithFollowers(Long id, int followerCount) {
		User user = user(id);
		ReflectionTestUtils.setField(user, "followerCount", followerCount);
		return user;
	}

	private static Post post(Long id) {
		Post post = Post.publish(user(99L), LocalDate.of(2026, 6, 21), AspectRatio.RATIO_3_4, false);
		ReflectionTestUtils.setField(post, "id", id);
		return post;
	}

	private static Post postWithRankScore(Long id, BigDecimal rankScore) {
		Post post = post(id);
		ReflectionTestUtils.setField(post, "rankScore", rankScore);
		return post;
	}

	private static Post postWithLikeCount(Long id, int likeCount) {
		Post post = post(id);
		ReflectionTestUtils.setField(post, "likeCount", likeCount);
		return post;
	}

	private static Post postWithCreatedAt(Long id, LocalDateTime createdAt) {
		Post post = post(id);
		ReflectionTestUtils.setField(post, "createdAt", createdAt);
		return post;
	}

	private static PostImage cover(Post post) {
		PostImage image = PostImage.of(post, "post/2026/09/a.jpg", 0, true);
		ReflectionTestUtils.setField(image, "id", 500L + post.getId());
		return image;
	}
}
