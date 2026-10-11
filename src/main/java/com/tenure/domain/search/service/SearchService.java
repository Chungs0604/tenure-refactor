package com.tenure.domain.search.service;

import com.tenure.domain.follow.repository.FollowRepository;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.repository.PostImageRepository;
import com.tenure.domain.search.dto.PostSearchCriteria;
import com.tenure.domain.search.dto.response.RecommendationResponse;
import com.tenure.domain.search.dto.response.SearchPostSummaryResponse;
import com.tenure.domain.search.dto.response.SuggestedKeywordResponse;
import com.tenure.domain.search.entity.SearchHistory;
import com.tenure.domain.search.enums.RecentViewTargetType;
import com.tenure.domain.search.enums.RecommendationSection;
import com.tenure.domain.search.mapper.SearchMapper;
import com.tenure.domain.search.repository.PostSearchRepository;
import com.tenure.domain.search.repository.PostSearchRow;
import com.tenure.domain.search.repository.RecommendationRepository;
import com.tenure.domain.search.repository.SearchHistoryRepository;
import com.tenure.domain.search.repository.UserSearchRepository;
import com.tenure.domain.search.store.RecentViewStore;
import com.tenure.domain.search.store.SuggestedKeywordCache;
import com.tenure.domain.user.dto.response.UserSummaryResponse;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.mapper.UserMapper;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.storage.FileStorage;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
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
 * 게시물·계정 검색과 추천 섹션(API-200·201·202·206·207). 검색 기록 삭제(API-203~205·208·209)는
 * {@code SearchHistoryService} 가 맡는다 — 조회·검색과 '내 기록 관리'는 트랜잭션 성격이 다르다
 * (여기는 부수효과로 기록만 남기고, 거기는 소유권 검증이 있는 삭제가 중심이다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

	/** 추천 메인(API-202)의 섹션별 미리보기 개수. 명세에 정확한 수가 없어 더보기(API-207) 한 페이지와 맞췄다 */
	private static final int RECOMMENDATION_PREVIEW_SIZE = 8;

	/** '방금 본 OOTD' 추천의 입력으로 쓰는 최근 열람 게시물 수. */
	private static final int RECENT_VIEW_SEED_LIMIT = 10;

	private final PostSearchRepository postSearchRepository;
	private final UserSearchRepository userSearchRepository;
	private final RecommendationRepository recommendationRepository;
	private final SearchHistoryRepository searchHistoryRepository;
	private final SuggestedKeywordCache suggestedKeywordCache;
	private final RecentViewStore recentViewStore;
	private final PostImageRepository postImageRepository;
	private final FollowRepository followRepository;
	private final UserRepository userRepository;
	private final SearchMapper searchMapper;
	private final UserMapper userMapper;
	private final FileStorage fileStorage;

	/** API-200. 검색어가 있으면 search_history 에 부수효과로 기록한다. */
	@Transactional
	@Loggable("게시물 검색")
	public PostSearchResult searchPosts(Long userId, PostSearchCriteria criteria, Cursor cursor, int size) {
		recordKeyword(userId, criteria.keyword());

		// 조건이 바뀌지 않는 한 모든 페이지의 총계가 같다 — 행이 없다고 0건을 돌려주면
		// 스크롤이 끝에 닿는 순간 화면 상단 '총 N개'가 0으로 바뀌어 보인다.
		int totalCount = (int) postSearchRepository.countMatches(criteria, userId);

		List<PostSearchRow> rows = postSearchRepository.search(criteria, userId, cursor, size);
		if (rows.isEmpty()) {
			return new PostSearchResult(CursorSlice.empty(), totalCount);
		}

		Map<Long, String> coverUrls = coverUrls(rows.stream().map(r -> r.post().getId()).toList());

		CursorSlice<SearchPostSummaryResponse> slice = CursorSlice.of(rows, size, PostSearchRow::cursor)
				.map(row -> toSearchPostSummary(row.post(), coverUrls));

		return new PostSearchResult(slice, totalCount);
	}

	/** API-201. API-200 과 같은 이유로 search_history 에 기록한다. */
	@Transactional
	@Loggable("계정 검색")
	public CursorSlice<UserSummaryResponse> searchUsers(
			Long viewerId, String keyword, Cursor cursor, int size
	) {
		recordKeyword(viewerId, keyword);

		List<User> rows = userSearchRepository.searchByNickname(keyword, viewerId, cursor, size);
		if (rows.isEmpty()) {
			return CursorSlice.empty();
		}

		Set<Long> followingIds = followingIdsAmong(viewerId, rows.stream().map(User::getId).toList());

		return CursorSlice.of(rows, size, u -> Cursor.of(u.getFollowerCount(), u.getId()))
				.map(u -> userMapper.toUserSummaryResponse(
						u, toUrl(u.getProfileImageKey()), followingIds.contains(u.getId())));
	}

	/** API-202. 섹션마다 미리보기만 — 더보기는 getRecommendationSection() 이 맡는다. */
	@Loggable("검색 메인 추천")
	public RecommendationResponse getRecommendations(Long userId) {
		List<Long> recentPostIds = recentViewStore.findRecentTargetIds(
				userId, RecentViewTargetType.POST, RECENT_VIEW_SEED_LIMIT);

		// 리포지토리는 hasNext 판단용으로 size + 1 건을 읽는다(더보기 API 와 같은 메서드를 공유한다).
		// 미리보기는 hasNext 가 필요 없으니 그 여분 한 건만 잘라낸다 — 안 자르면 섹션마다 9개가 나간다.
		List<Post> similarPosts = recommendationRepository
				.findSimilarToRecent(recentPostIds, userId, null, RECOMMENDATION_PREVIEW_SIZE)
				.stream().limit(RECOMMENDATION_PREVIEW_SIZE).toList();
		List<Post> popularPosts = recommendationRepository
				.findPopularStyle(userId, null, RECOMMENDATION_PREVIEW_SIZE)
				.stream().limit(RECOMMENDATION_PREVIEW_SIZE).toList();
		List<Post> latestPosts = recommendationRepository
				.findNewOotd(userId, null, RECOMMENDATION_PREVIEW_SIZE)
				.stream().limit(RECOMMENDATION_PREVIEW_SIZE).toList();
		List<User> popularUsers = recommendationRepository
				.findPopularUsers(userId, null, RECOMMENDATION_PREVIEW_SIZE)
				.stream().limit(RECOMMENDATION_PREVIEW_SIZE).toList();

		Map<Long, String> coverUrls = coverUrls(
				concatIds(similarPosts, popularPosts, latestPosts));
		Set<Long> followingIds = followingIdsAmong(userId, popularUsers.stream().map(User::getId).toList());

		return RecommendationResponse.builder()
				.similarPosts(similarPosts.stream().map(p -> toSearchPostSummary(p, coverUrls)).toList())
				.popularPosts(popularPosts.stream().map(p -> toSearchPostSummary(p, coverUrls)).toList())
				.latestPosts(latestPosts.stream().map(p -> toSearchPostSummary(p, coverUrls)).toList())
				.popularUsers(popularUsers.stream()
						.map(u -> userMapper.toUserSummaryResponse(
								u, toUrl(u.getProfileImageKey()), followingIds.contains(u.getId())))
						.toList())
				.build();
	}

	/** API-206. 캐시 미스면 집계해서 채운다(DEC-020). DB 는 읽기만 하고 캐시는 Redis 라 쓰기 트랜잭션이 필요 없다. */
	@Loggable("추천 검색어")
	public List<SuggestedKeywordResponse> getSuggestedKeywords() {
		List<String> cached = suggestedKeywordCache.get();
		List<String> keywords = cached.isEmpty() ? refreshSuggestedKeywords() : cached;

		return keywords.stream()
				.map(keyword -> SuggestedKeywordResponse.builder().keyword(keyword).build())
				.toList();
	}

	private List<String> refreshSuggestedKeywords() {
		// '최근 N일' 이 명세에 정해진 값이 아니다 — 트렌드가 그 안에 바뀌는 정도로 넉넉히 잡았다
		List<String> keywords = searchHistoryRepository.findTrendingKeywords(LocalDateTime.now().minusDays(7), 10);
		suggestedKeywordCache.put(keywords);
		return keywords;
	}

	/** API-207. POPULAR_USER 만 사용자 목록이라 반환형이 CursorSlice<?> 다. */
	@Loggable("추천 섹션 더보기")
	public CursorSlice<?> getRecommendationSection(
			Long userId, RecommendationSection section, Cursor cursor, int size
	) {
		if (section == RecommendationSection.POPULAR_USER) {
			List<User> rows = recommendationRepository.findPopularUsers(userId, cursor, size);
			if (rows.isEmpty()) {
				return CursorSlice.empty();
			}
			Set<Long> followingIds = followingIdsAmong(userId, rows.stream().map(User::getId).toList());
			return CursorSlice.of(rows, size, u -> Cursor.of(u.getFollowerCount(), u.getId()))
					.map(u -> userMapper.toUserSummaryResponse(
							u, toUrl(u.getProfileImageKey()), followingIds.contains(u.getId())));
		}

		List<Post> rows = switch (section) {
			case SIMILAR_TO_RECENT -> {
				List<Long> recentPostIds = recentViewStore.findRecentTargetIds(
						userId, RecentViewTargetType.POST, RECENT_VIEW_SEED_LIMIT);
				yield recommendationRepository.findSimilarToRecent(recentPostIds, userId, cursor, size);
			}
			case POPULAR_STYLE -> recommendationRepository.findPopularStyle(userId, cursor, size);
			case NEW_OOTD -> recommendationRepository.findNewOotd(userId, cursor, size);
			case POPULAR_USER -> throw new IllegalStateException("위에서 이미 처리함");
		};

		if (rows.isEmpty()) {
			return CursorSlice.empty();
		}

		Map<Long, String> coverUrls = coverUrls(rows.stream().map(Post::getId).toList());
		Function<Post, Cursor> cursorOf = switch (section) {
			case SIMILAR_TO_RECENT -> p -> Cursor.of(p.getRankScore(), p.getId());
			case POPULAR_STYLE -> p -> Cursor.of(p.getLikeCount(), p.getId());
			case NEW_OOTD -> p -> Cursor.of(p.getCreatedAt(), p.getId());
			case POPULAR_USER -> throw new IllegalStateException("위에서 이미 처리함");
		};

		return CursorSlice.of(rows, size, cursorOf)
				.map(p -> toSearchPostSummary(p, coverUrls));
	}

	private void recordKeyword(Long userId, String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return;
		}

		User user = userRepository.findById(userId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 사용자입니다. userId={}", userId);
					return new CustomException(UserErrorCode.USER_404);
				});

		searchHistoryRepository.save(SearchHistory.record(user, keyword.trim(), LocalDateTime.now()));
	}

	private SearchPostSummaryResponse toSearchPostSummary(Post post, Map<Long, String> coverUrls) {
		return searchMapper.toSearchPostSummaryResponse(post, coverUrls.get(post.getId()));
	}

	@SafeVarargs
	private List<Long> concatIds(List<Post>... lists) {
		return Arrays.stream(lists).flatMap(List::stream).map(Post::getId).distinct().toList();
	}

	private Map<Long, String> coverUrls(List<Long> postIds) {
		if (postIds.isEmpty()) {
			return Map.of();
		}
		return postImageRepository.findCovers(postIds).stream()
				.collect(Collectors.toMap(image -> image.getPost().getId(), image -> toUrl(image.getImageKey())));
	}

	private Set<Long> followingIdsAmong(Long viewerId, Collection<Long> candidateIds) {
		return candidateIds.isEmpty()
				? Set.of()
				: new HashSet<>(followRepository.findFollowingIdsAmong(viewerId, candidateIds));
	}

	private String toUrl(String key) {
		return key == null ? null : fileStorage.toUrl(key);
	}

	/** Controller 가 totalCount 를 응답에 합치는 데 쓴다. */
	public record PostSearchResult(CursorSlice<SearchPostSummaryResponse> slice, int totalCount) {
	}
}
