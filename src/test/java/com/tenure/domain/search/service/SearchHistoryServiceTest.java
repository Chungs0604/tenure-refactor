package com.tenure.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.tenure.domain.block.repository.UserBlockRepository;
import com.tenure.domain.search.entity.SearchHistory;
import com.tenure.domain.search.enums.RecentViewTargetType;
import com.tenure.domain.search.exception.SearchErrorCode;
import com.tenure.domain.search.repository.SearchHistoryRepository;
import com.tenure.domain.search.store.RecentViewStore;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CommonErrorCode;
import com.tenure.global.exception.CustomException;
import com.tenure.global.storage.FileStorage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SearchHistoryService")
class SearchHistoryServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long OTHER_USER_ID = 2L;
	private static final Long HISTORY_ID = 100L;
	private static final Long TARGET_ID = 200L;

	@Mock private SearchHistoryRepository searchHistoryRepository;
	@Mock private RecentViewStore recentViewStore;
	@Mock private UserRepository userRepository;
	@Mock private UserBlockRepository userBlockRepository;
	@Mock private FileStorage fileStorage;

	@InjectMocks private SearchHistoryService searchHistoryService;

	@Nested
	@DisplayName("최근 검색어 개별 삭제 (API-204)")
	class DeleteKeyword {

		@Test
		@DisplayName("본인 검색 기록이면 지운다")
		void deleteKeyword_owner_deletes() {
			SearchHistory history = historyOf(USER_ID);
			given(searchHistoryRepository.findById(HISTORY_ID)).willReturn(Optional.of(history));

			searchHistoryService.deleteKeyword(USER_ID, HISTORY_ID);

			verify(searchHistoryRepository).delete(history);
		}

		@Test
		@DisplayName("남의 검색 기록이면 COMMON_403 이고 지우지 않는다")
		void deleteKeyword_notOwner_throwsForbidden() {
			SearchHistory history = historyOf(OTHER_USER_ID);
			given(searchHistoryRepository.findById(HISTORY_ID)).willReturn(Optional.of(history));

			assertThatThrownBy(() -> searchHistoryService.deleteKeyword(USER_ID, HISTORY_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(CommonErrorCode.FORBIDDEN);

			verify(searchHistoryRepository, never()).delete(any());
		}

		@Test
		@DisplayName("존재하지 않으면 SEARCH_404_HISTORY 다")
		void deleteKeyword_missing_throwsNotFound() {
			given(searchHistoryRepository.findById(HISTORY_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> searchHistoryService.deleteKeyword(USER_ID, HISTORY_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(SearchErrorCode.SEARCH_404_HISTORY);
		}
	}

	@Nested
	@DisplayName("최근 본 사용자 삭제 (API-208·209)")
	class RecentUserDeletion {

		@Test
		@DisplayName("개별 삭제는 소유권 검증 없이 Redis 에서 바로 지운다 — 키 자체가 userId 로 네임스페이스돼 있다")
		void removeRecentUser_removesFromRedis() {
			searchHistoryService.removeRecentUser(USER_ID, TARGET_ID);

			verify(recentViewStore).remove(USER_ID, RecentViewTargetType.USER, TARGET_ID);
		}

		@Test
		@DisplayName("전체 삭제는 USER 유형만 지운다 — '방금 보신 OOTD' 추천 입력(POST 유형)은 남긴다")
		void clearRecentUsers_onlyClearsUserType() {
			searchHistoryService.clearRecentUsers(USER_ID);

			verify(recentViewStore).deleteByType(USER_ID, RecentViewTargetType.USER);
			verify(recentViewStore, never()).deleteByType(USER_ID, RecentViewTargetType.POST);
		}
	}

	@Nested
	@DisplayName("조회 (API-203)")
	class GetHistories {

		@Test
		@DisplayName("최근 검색어와 최근 본 사용자를 함께 내려준다")
		void getHistories_assemblesBoth() {
			SearchHistory history = historyOf(USER_ID);
			given(searchHistoryRepository.findRecent(USER_ID, 20)).willReturn(List.of(history));

			given(recentViewStore.findRecentTargetIds(USER_ID, RecentViewTargetType.USER, 20))
					.willReturn(List.of(TARGET_ID));
			User recentUser = user(TARGET_ID);
			given(userRepository.findAllById(List.of(TARGET_ID))).willReturn(List.of(recentUser));
			given(fileStorage.toUrl(any())).willReturn("https://cdn.tenure.app/x.jpg");

			var response = searchHistoryService.getHistories(USER_ID);

			assertThat(response.getKeywords()).hasSize(1);
			assertThat(response.getKeywords().get(0).getId()).isEqualTo(HISTORY_ID);
			assertThat(response.getRecentUsers()).hasSize(1);
			assertThat(response.getRecentUsers().get(0).getId()).isEqualTo(TARGET_ID);
		}

		@Test
		@DisplayName("열람 이후 차단 관계가 생긴 사용자는 뺀다 — 검색·추천과 같은 기준")
		void getHistories_blockedUser_isExcluded() {
			given(searchHistoryRepository.findRecent(USER_ID, 20)).willReturn(List.of());
			given(recentViewStore.findRecentTargetIds(USER_ID, RecentViewTargetType.USER, 20))
					.willReturn(List.of(TARGET_ID));
			given(userRepository.findAllById(List.of(TARGET_ID))).willReturn(List.of(user(TARGET_ID)));
			given(userBlockRepository.existsMutualBlock(USER_ID, TARGET_ID)).willReturn(true);

			var response = searchHistoryService.getHistories(USER_ID);

			assertThat(response.getRecentUsers()).isEmpty();
		}

		@Test
		@DisplayName("Redis 에는 남아 있지만 탈퇴 등으로 유저 행이 사라진 경우는 건너뛴다")
		void getHistories_missingUser_skipsDanglingId() {
			given(searchHistoryRepository.findRecent(USER_ID, 20)).willReturn(List.of());
			given(recentViewStore.findRecentTargetIds(USER_ID, RecentViewTargetType.USER, 20))
					.willReturn(List.of(TARGET_ID));
			given(userRepository.findAllById(List.of(TARGET_ID))).willReturn(List.of());

			var response = searchHistoryService.getHistories(USER_ID);

			assertThat(response.getRecentUsers()).isEmpty();
		}
	}

	private static SearchHistory historyOf(Long userId) {
		User owner = user(userId);
		SearchHistory history = SearchHistory.record(owner, "데님", LocalDateTime.now());
		ReflectionTestUtils.setField(history, "id", HISTORY_ID);
		return history;
	}

	private static User user(Long id) {
		User user = User.signUp("user" + id + "@tenure.app", "$2a$10$hashed", "홍길동",
				"821012345678", "nick" + id, null, Gender.FEMALE, 165, 55);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}
}
