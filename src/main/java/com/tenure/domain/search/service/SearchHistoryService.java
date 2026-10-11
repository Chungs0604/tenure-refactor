package com.tenure.domain.search.service;

import com.tenure.domain.block.repository.UserBlockRepository;
import com.tenure.domain.search.dto.response.SearchHistoryResponse;
import com.tenure.domain.search.entity.SearchHistory;
import com.tenure.domain.search.enums.RecentViewTargetType;
import com.tenure.domain.search.exception.SearchErrorCode;
import com.tenure.domain.search.repository.SearchHistoryRepository;
import com.tenure.domain.search.store.RecentViewStore;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.mapper.UserMapper;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CommonErrorCode;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import com.tenure.global.storage.FileStorage;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최근 검색어·최근 본 사용자의 조회와 삭제 (API-203~205, 208~209).
 *
 * 둘은 저장소가 다르다 — 검색어는 RDB(SearchHistory, 개별 PK 로 삭제), 최근 본 사용자는
 * Redis Sorted Set(target id 로 바로 ZREM, 소유권 검증이 필요 없다 — 키 자체가
 * {@code userId} 로 네임스페이스돼 있어 남의 기록을 지울 방법이 없다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchHistoryService {

	/** 최근 검색어 표시 상한. 명세에 정해진 값이 아니다 — RecentViewStore.MAX_SIZE 와 맞췄다 */
	private static final int RECENT_KEYWORD_LIMIT = 20;

	private static final int RECENT_USER_LIMIT = 20;

	private final SearchHistoryRepository searchHistoryRepository;
	private final RecentViewStore recentViewStore;
	private final UserRepository userRepository;
	private final UserBlockRepository userBlockRepository;
	private final FileStorage fileStorage;

	/** API-203. */
	@Loggable("최근 검색어·최근 본 사용자 조회")
	public SearchHistoryResponse getHistories(Long userId) {
		List<SearchHistory> keywords = searchHistoryRepository.findRecent(userId, RECENT_KEYWORD_LIMIT);

		List<Long> recentUserIds = recentViewStore.findRecentTargetIds(
				userId, RecentViewTargetType.USER, RECENT_USER_LIMIT);
		Map<Long, User> usersById = recentUserIds.isEmpty()
				? Map.of()
				: userRepository.findAllById(recentUserIds).stream()
						.collect(Collectors.toMap(User::getId, Function.identity()));

		return SearchHistoryResponse.builder()
				.keywords(keywords.stream()
						.map(h -> SearchHistoryResponse.Keyword.builder()
								.id(h.getId())
								.keyword(h.getKeyword())
								.searchedAt(h.getSearchedAt())
								.build())
						.toList())
				.recentUsers(recentUserIds.stream()
						.map(usersById::get)
						// Redis 는 남아 있지만 탈퇴 등으로 유저 행이 사라진 경우를 건너뛴다
						.filter(u -> u != null)
						// 열람 이후 차단 관계가 생겼으면 뺀다 — 검색·추천과 같은 기준(BlockPredicates)
						.filter(u -> !userBlockRepository.existsMutualBlock(userId, u.getId()))
						.map(u -> SearchHistoryResponse.RecentUser.builder()
								.id(u.getId())
								.nickname(UserMapper.displayNickname(u))
								.profileImageUrl(toUrl(u.getProfileImageKey()))
								.build())
						.toList())
				.build();
	}

	/** API-204. 소유자가 아니면 403 — 남의 검색어를 지울 수 없다. */
	@Transactional
	@Loggable("최근 검색어 개별 삭제")
	public void deleteKeyword(Long userId, Long historyId) {
		SearchHistory history = searchHistoryRepository.findById(historyId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 검색 기록입니다. historyId={}", historyId);
					return new CustomException(SearchErrorCode.SEARCH_404_HISTORY);
				});

		if (!history.getUser().getId().equals(userId)) {
			log.warn("본인의 검색 기록이 아닙니다. historyId={}, userId={}", historyId, userId);
			throw new CustomException(CommonErrorCode.FORBIDDEN);
		}

		searchHistoryRepository.delete(history);
	}

	/** API-205. */
	@Transactional
	@Loggable("최근 검색어 전체 삭제")
	public void deleteAllKeywords(Long userId) {
		searchHistoryRepository.deleteAllByUserId(userId);
	}

	/** API-208. Redis ZREM 은 멱등이라 대상이 이미 없어도 그냥 성공한다. */
	@Transactional
	@Loggable("최근 본 사용자 개별 삭제")
	public void removeRecentUser(Long userId, Long targetId) {
		recentViewStore.remove(userId, RecentViewTargetType.USER, targetId);
	}

	/** API-209. '방금 보신 OOTD' 추천의 입력(POST 유형)은 남긴다 — 화면의 '지우기'는 이 섹션뿐이다. */
	@Transactional
	@Loggable("최근 본 사용자 전체 삭제")
	public void clearRecentUsers(Long userId) {
		recentViewStore.deleteByType(userId, RecentViewTargetType.USER);
	}

	private String toUrl(String key) {
		return key == null ? null : fileStorage.toUrl(key);
	}
}
