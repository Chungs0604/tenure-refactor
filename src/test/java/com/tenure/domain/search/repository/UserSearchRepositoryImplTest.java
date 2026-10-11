package com.tenure.domain.search.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.support.RepositorySliceTestSupport;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 계정 검색(API-201). 닉네임 부분 일치 + 팔로워 수 순 + 차단 제외. */
@DisplayName("계정 검색 쿼리")
@Import(UserSearchRepositoryImpl.class)
class UserSearchRepositoryImplTest extends RepositorySliceTestSupport {

	private static final int SIZE = 20;

	@Autowired private EntityManager em;
	@Autowired private UserSearchRepository userSearchRepository;

	private User viewer;

	@BeforeEach
	void setUp() {
		viewer = persistUser("viewer@tenure.app", "Viewer", 0);
	}

	@Nested
	@DisplayName("닉네임 검색")
	class SearchByNickname {

		@Test
		@DisplayName("부분 일치하는 닉네임만 담는다")
		void searchByNickname_partialMatch_returnsMatchingOnly() {
			User matching = persistUser("sujun@tenure.app", "Sujun", 0);
			persistUser("yujin@tenure.app", "YuJin", 0);

			assertThat(userSearchRepository.searchByNickname("suj", viewer.getId(), null, SIZE))
					.extracting(User::getId).containsExactly(matching.getId());
		}

		@Test
		@DisplayName("팔로워 수 내림차순이다")
		void searchByNickname_ordersByFollowerCountDesc() {
			User low = persistUser("low@tenure.app", "SearchLow", 1);
			User high = persistUser("high@tenure.app", "SearchHigh", 10);

			assertThat(userSearchRepository.searchByNickname("Search", viewer.getId(), null, SIZE))
					.extracting(User::getId).containsExactly(high.getId(), low.getId());
		}

		@Test
		@DisplayName("내가 차단한, 나를 차단한 사용자는 양방향으로 빠진다")
		void searchByNickname_blocked_excludedBothWays() {
			User blockedByMe = persistUser("blockedbyme@tenure.app", "SearchBlockedByMe", 0);
			User blocksMe = persistUser("blocksme@tenure.app", "SearchBlocksMe", 0);
			block(viewer, blockedByMe);
			block(blocksMe, viewer);

			assertThat(userSearchRepository.searchByNickname("Search", viewer.getId(), null, SIZE))
					.extracting(User::getId).doesNotContain(blockedByMe.getId(), blocksMe.getId());
		}
	}

	private User persistUser(String email, String nickname, int followerCount) {
		User user = User.signUp(email, "$2a$10$hashed", "홍길동",
				"821012345678", nickname, null, Gender.MALE, 170, 64);
		em.persist(user);
		em.flush();
		if (followerCount > 0) {
			em.createNativeQuery("update user set follower_count = ? where id = ?")
					.setParameter(1, followerCount)
					.setParameter(2, user.getId())
					.executeUpdate();
			em.clear();
		}
		return user;
	}

	private void block(User blocker, User blocked) {
		em.createNativeQuery("insert into user_block (blocker_id, blocked_id, created_at) "
						+ "values (?, ?, now(6))")
				.setParameter(1, blocker.getId())
				.setParameter(2, blocked.getId())
				.executeUpdate();
	}
}
