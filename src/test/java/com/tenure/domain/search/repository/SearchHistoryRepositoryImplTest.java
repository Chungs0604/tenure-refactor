package com.tenure.domain.search.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.search.entity.SearchHistory;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.support.RepositorySliceTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 최근 검색어 조회(API-203)·추천 검색어 집계(API-206). */
@DisplayName("검색 기록 쿼리")
class SearchHistoryRepositoryImplTest extends RepositorySliceTestSupport {

	@Autowired private EntityManager em;
	@Autowired private SearchHistoryRepository searchHistoryRepository;

	private User me;
	private User other;

	@BeforeEach
	void setUp() {
		me = persistUser("me@tenure.app", "Me");
		other = persistUser("other@tenure.app", "Other");
	}

	@Nested
	@DisplayName("최근 검색어")
	class FindRecent {

		@Test
		@DisplayName("검색한 시각 내림차순이고, 같은 키워드를 다시 검색해도 중복 제거 없이 쌓인다")
		void findRecent_ordersByRecencyAndKeepsDuplicates() {
			persistHistory(me, "데님", LocalDateTime.now().minusMinutes(3));
			SearchHistory second = persistHistory(me, "데님", LocalDateTime.now().minusMinutes(2));
			SearchHistory third = persistHistory(me, "반팔", LocalDateTime.now().minusMinutes(1));

			List<SearchHistory> result = searchHistoryRepository.findRecent(me.getId(), 20);

			assertThat(result).hasSize(3);
			assertThat(result.get(0).getId()).isEqualTo(third.getId());
			assertThat(result.get(1).getId()).isEqualTo(second.getId());
		}

		@Test
		@DisplayName("다른 사용자의 검색어는 섞이지 않는다")
		void findRecent_onlyOwnHistory() {
			persistHistory(me, "데님", LocalDateTime.now());
			persistHistory(other, "셔츠", LocalDateTime.now());

			assertThat(searchHistoryRepository.findRecent(me.getId(), 20))
					.extracting(SearchHistory::getKeyword).containsExactly("데님");
		}

		@Test
		@DisplayName("limit 건수만 돌려준다")
		void findRecent_respectsLimit() {
			for (int i = 0; i < 5; i++) {
				persistHistory(me, "키워드" + i, LocalDateTime.now().minusMinutes(i));
			}

			assertThat(searchHistoryRepository.findRecent(me.getId(), 2)).hasSize(2);
		}
	}

	@Nested
	@DisplayName("추천 검색어 집계")
	class FindTrendingKeywords {

		@Test
		@DisplayName("키워드별 건수가 많은 순이다")
		void findTrendingKeywords_ordersByCountDesc() {
			persistHistory(me, "데님", LocalDateTime.now());
			persistHistory(other, "데님", LocalDateTime.now());
			persistHistory(me, "반팔", LocalDateTime.now());

			assertThat(searchHistoryRepository.findTrendingKeywords(LocalDateTime.now().minusDays(1), 10))
					.containsExactly("데님", "반팔");
		}

		@Test
		@DisplayName("기준 시각 이전의 검색은 집계에서 빠진다")
		void findTrendingKeywords_excludesBeforeSince() {
			persistHistory(me, "오래된", LocalDateTime.now().minusDays(10));
			persistHistory(me, "최근", LocalDateTime.now());

			assertThat(searchHistoryRepository.findTrendingKeywords(LocalDateTime.now().minusDays(1), 10))
					.containsExactly("최근");
		}

		@Test
		@DisplayName("limit 건수만 돌려준다")
		void findTrendingKeywords_respectsLimit() {
			for (int i = 0; i < 5; i++) {
				persistHistory(me, "키워드" + i, LocalDateTime.now());
			}

			assertThat(searchHistoryRepository.findTrendingKeywords(LocalDateTime.now().minusDays(1), 2))
					.hasSize(2);
		}
	}

	private User persistUser(String email, String nickname) {
		User user = User.signUp(email, "$2a$10$hashed", "홍길동",
				"821012345678", nickname, null, Gender.MALE, 170, 64);
		em.persist(user);
		em.flush();
		return user;
	}

	private SearchHistory persistHistory(User user, String keyword, LocalDateTime searchedAt) {
		SearchHistory history = SearchHistory.record(user, keyword, searchedAt);
		em.persist(history);
		em.flush();
		return history;
	}
}
