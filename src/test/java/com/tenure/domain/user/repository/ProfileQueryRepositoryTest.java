package com.tenure.domain.user.repository;

import com.tenure.domain.block.repository.UserBlockRepository;
import com.tenure.domain.follow.repository.FollowRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.support.RepositorySliceTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로필 화면을 받치는 {@code @Query} 4개를 한자리에서 검증한다.
 *
 * <p>리포지토리별로 파일을 쪼개지 않은 이유 — 네 쿼리가 같은 화면(내 프로필·타인 프로필)을 위해
 * 함께 호출되고 픽스처(사용자 2명·카테고리)를 공유한다.
 *
 * <p>{@code Follow}·{@code UserBlock}·{@code Item} 에는 정적 팩토리가 없다. 테스트 편의로
 * 프로덕션 코드를 늘리지 않고 네이티브 SQL 로 넣는다.
 */
@DisplayName("프로필 조회 쿼리")
class ProfileQueryRepositoryTest extends RepositorySliceTestSupport {

	@Autowired private UserRepository userRepository;
	@Autowired private FollowRepository followRepository;
	@Autowired private UserBlockRepository userBlockRepository;
	@Autowired private ItemRepository itemRepository;
	@Autowired private EntityManager em;

	private User me;
	private User other;

	@BeforeEach
	void setUp() {
		me = userRepository.save(user("me@tenure.local", "Sujun"));
		other = userRepository.save(user("other@tenure.local", "Yujin"));
		em.flush();
	}

	@Nested
	@DisplayName("existsOtherNickname")
	class ExistsOtherNickname {

		@Test
		@DisplayName("자기 닉네임은 중복으로 보지 않는다")
		void ownNickname_notDuplicate() {
			// collation 이 대소문자를 무시해 existsByNickname 으로는 자기 것도 걸린다
			assertThat(userRepository.existsOtherNickname("Sujun", me.getId())).isFalse();
		}

		@Test
		@DisplayName("다른 사용자가 쓰는 닉네임이면 중복이다")
		void othersNickname_duplicate() {
			assertThat(userRepository.existsOtherNickname("Yujin", me.getId())).isTrue();
		}

		@Test
		@DisplayName("대소문자만 다른 닉네임도 중복이다")
		void differentCaseOnly_duplicate() {
			// utf8mb4_0900_ai_ci 는 대소문자를 무시한다. 자바에서 비교했다면 놓쳤을 경우다
			assertThat(userRepository.existsOtherNickname("yujin", me.getId())).isTrue();
		}

		@Test
		@DisplayName("아무도 안 쓰는 닉네임이면 중복이 아니다")
		void unusedNickname_notDuplicate() {
			assertThat(userRepository.existsOtherNickname("nobody", me.getId())).isFalse();
		}
	}

	@Nested
	@DisplayName("existsFollow")
	class ExistsFollow {

		@Test
		@DisplayName("팔로우한 방향만 true 다")
		void follow_directional() {
			insertFollow(me.getId(), other.getId());

			assertThat(followRepository.existsFollow(me.getId(), other.getId())).isTrue();
			// 맞팔이 아니면 역방향은 false — 프로필의 팔로우 버튼이 반대로 뜨면 안 된다
			assertThat(followRepository.existsFollow(other.getId(), me.getId())).isFalse();
		}

		@Test
		@DisplayName("팔로우가 없으면 false 다")
		void noFollow_false() {
			assertThat(followRepository.existsFollow(me.getId(), other.getId())).isFalse();
		}
	}

	@Nested
	@DisplayName("existsBlock")
	class ExistsBlock {

		@Test
		@DisplayName("차단한 방향만 true 다")
		void block_directional() {
			insertBlock(me.getId(), other.getId());

			assertThat(userBlockRepository.existsBlock(me.getId(), other.getId())).isTrue();
			// 상대가 나를 차단한 경우는 이 응답에 싣지 않는다
			assertThat(userBlockRepository.existsBlock(other.getId(), me.getId())).isFalse();
		}

		@Test
		@DisplayName("차단이 없으면 false 다")
		void noBlock_false() {
			assertThat(userBlockRepository.existsBlock(me.getId(), other.getId())).isFalse();
		}
	}

	@Nested
	@DisplayName("countRegisteredItem")
	class CountRegisteredItem {

		@Test
		@DisplayName("삭제한 아이템은 세지 않는다")
		void deletedItems_excluded() {
			insertItem(me.getId(), "살아있는 1", false);
			insertItem(me.getId(), "살아있는 2", false);
			insertItem(me.getId(), "지운 것", true);

			// 등록한 아이템 목록 화면과 조건이 같아야 수치와 목록이 어긋나지 않는다
			assertThat(itemRepository.countRegisteredItem(me.getId())).isEqualTo(2);
		}

		@Test
		@DisplayName("다른 사용자가 등록한 아이템은 세지 않는다")
		void othersItems_excluded() {
			insertItem(me.getId(), "내 것", false);
			insertItem(other.getId(), "남의 것", false);

			assertThat(itemRepository.countRegisteredItem(me.getId())).isEqualTo(1);
		}

		@Test
		@DisplayName("아이템이 없으면 0 이다")
		void noItems_zero() {
			assertThat(itemRepository.countRegisteredItem(me.getId())).isZero();
		}
	}

	private void insertFollow(Long followerId, Long followingId) {
		em.createNativeQuery("""
						insert into follow (follower_id, following_id, created_at)
						 values (?, ?, now(6))""")
				.setParameter(1, followerId).setParameter(2, followingId)
				.executeUpdate();
	}

	private void insertBlock(Long blockerId, Long blockedId) {
		em.createNativeQuery("""
						insert into user_block (blocker_id, blocked_id, created_at)
						 values (?, ?, now(6))""")
				.setParameter(1, blockerId).setParameter(2, blockedId)
				.executeUpdate();
	}

	private void insertItem(Long registrantId, String name, boolean deleted) {
		Object categoryId = em.createNativeQuery(
				"select id from category where depth = 2 order by id limit 1").getSingleResult();

		em.createNativeQuery("""
						insert into item (registrant_id, name, brand_name, category_id,
						                  cover_image_key, deleted_at, created_at, updated_at)
						 values (?, ?, 'Levis', ?, 'item/cover.jpg', %s, now(6), now(6))"""
						.formatted(deleted ? "now(6)" : "null"))
				.setParameter(1, registrantId).setParameter(2, name).setParameter(3, categoryId)
				.executeUpdate();
	}

	private static User user(String email, String nickname) {
		return User.signUp(email, "$2a$10$hashed", "홍길동", "821012345678",
				nickname, null, Gender.MALE, 170, 64);
	}
}
