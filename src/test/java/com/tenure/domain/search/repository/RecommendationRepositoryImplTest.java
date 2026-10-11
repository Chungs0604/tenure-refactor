package com.tenure.domain.search.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.entity.PostImage;
import com.tenure.domain.post.entity.PostItemTag;
import com.tenure.domain.post.enums.AspectRatio;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.global.pagination.Cursor;
import com.tenure.support.RepositorySliceTestSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 검색 메인 추천 4블록(API-202/207)의 공통 조회. */
@DisplayName("추천 블록 조회 쿼리")
@Import(RecommendationRepositoryImpl.class)
class RecommendationRepositoryImplTest extends RepositorySliceTestSupport {

	private static final int SIZE = 20;

	@Autowired private EntityManager em;
	@Autowired private RecommendationRepository recommendationRepository;

	private User viewer;
	private User author;

	@BeforeEach
	void setUp() {
		viewer = persistUser("viewer@tenure.app", "Viewer");
		author = persistUser("author@tenure.app", "Author");
	}

	@Nested
	@DisplayName("방금 보신 OOTD와 유사한 게시물")
	class SimilarToRecent {

		@Test
		@DisplayName("씨드 게시물이 없으면 바로 빈 목록이다 — 쿼리를 쏘지 않는다")
		void findSimilarToRecent_noSeed_returnsEmpty() {
			assertThat(recommendationRepository.findSimilarToRecent(List.of(), viewer.getId(), null, SIZE))
					.isEmpty();
		}

		@Test
		@DisplayName("씨드와 카테고리가 겹치는 게시물만 담고, 겹치지 않으면 뺀다")
		void findSimilarToRecent_sharesCategory_includesOnlyOverlapping() {
			Item seedItem = persistItem(101L);
			Post seedPost = persistPost(author);
			tag(seedPost, seedItem);

			Item sameCategoryItem = persistItem(101L);
			Post candidate = persistPost(author);
			tag(candidate, sameCategoryItem);

			Item otherCategoryItem = persistItem(128L);
			Post nonOverlapping = persistPost(author);
			tag(nonOverlapping, otherCategoryItem);

			List<Post> result = recommendationRepository
					.findSimilarToRecent(List.of(seedPost.getId()), viewer.getId(), null, SIZE);

			assertThat(result).extracting(Post::getId)
					.contains(candidate.getId())
					.doesNotContain(seedPost.getId(), nonOverlapping.getId());
		}

		@Test
		@DisplayName("씨드 게시물 자신은 겹치는 카테고리가 같아도 결과에서 빠진다")
		void findSimilarToRecent_seedItself_isExcluded() {
			Item seedItem = persistItem(101L);
			Post seedPost = persistPost(author);
			tag(seedPost, seedItem);

			assertThat(recommendationRepository
					.findSimilarToRecent(List.of(seedPost.getId()), viewer.getId(), null, SIZE))
					.extracting(Post::getId).doesNotContain(seedPost.getId());
		}

		@Test
		@DisplayName("차단 관계의 게시물은 양방향으로 제외된다")
		void findSimilarToRecent_blocked_excludedBothWays() {
			Item seedItem = persistItem(101L);
			Post seedPost = persistPost(author);
			tag(seedPost, seedItem);

			User blocked = persistUser("blocked@tenure.app", "Blocked");
			Item blockedItem = persistItem(101L);
			Post blockedPost = persistPost(blocked);
			tag(blockedPost, blockedItem);
			block(viewer, blocked);

			assertThat(recommendationRepository
					.findSimilarToRecent(List.of(seedPost.getId()), viewer.getId(), null, SIZE))
					.extracting(Post::getId).doesNotContain(blockedPost.getId());
		}
	}

	@Nested
	@DisplayName("지금 인기 있는 스타일")
	class PopularStyle {

		@Test
		@DisplayName("최근 30일 밖의 게시물은 좋아요가 많아도 빠진다")
		void findPopularStyle_outsideWindow_excluded() {
			Post recent = persistPost(author);
			setLikeCount(recent, 5);
			Post old = persistPost(author);
			setLikeCount(old, 100);
			backdateCreatedAt(old, LocalDateTime.now().minusDays(31));

			List<Post> result = recommendationRepository.findPopularStyle(viewer.getId(), null, SIZE);

			assertThat(result).extracting(Post::getId)
					.contains(recent.getId()).doesNotContain(old.getId());
		}

		@Test
		@DisplayName("좋아요 수 내림차순이다")
		void findPopularStyle_ordersByLikeCountDesc() {
			Post low = persistPost(author);
			setLikeCount(low, 1);
			Post high = persistPost(author);
			setLikeCount(high, 10);

			assertThat(recommendationRepository.findPopularStyle(viewer.getId(), null, SIZE))
					.extracting(Post::getId).containsExactly(high.getId(), low.getId());
		}

		@Test
		@DisplayName("차단 관계의 게시물은 제외된다")
		void findPopularStyle_blocked_excluded() {
			User blocker = persistUser("blocker@tenure.app", "Blocker");
			Post blockerPost = persistPost(blocker);
			block(blocker, viewer);

			assertThat(recommendationRepository.findPopularStyle(viewer.getId(), null, SIZE))
					.extracting(Post::getId).doesNotContain(blockerPost.getId());
		}
	}

	@Nested
	@DisplayName("새로 올라온 OOTD")
	class NewOotd {

		@Test
		@DisplayName("게시일 내림차순이다")
		void findNewOotd_ordersByCreatedAtDesc() {
			Post older = persistPost(author);
			backdateCreatedAt(older, LocalDateTime.now().minusDays(2));
			Post newer = persistPost(author);
			backdateCreatedAt(newer, LocalDateTime.now().minusDays(1));

			assertThat(recommendationRepository.findNewOotd(viewer.getId(), null, SIZE))
					.extracting(Post::getId).containsExactly(newer.getId(), older.getId());
		}

		@Test
		@DisplayName("차단 관계의 게시물은 제외된다")
		void findNewOotd_blocked_excluded() {
			User blocked = persistUser("blocked@tenure.app", "Blocked");
			Post blockedPost = persistPost(blocked);
			block(viewer, blocked);

			assertThat(recommendationRepository.findNewOotd(viewer.getId(), null, SIZE))
					.extracting(Post::getId).doesNotContain(blockedPost.getId());
		}
	}

	@Nested
	@DisplayName("인기 사용자")
	class PopularUsers {

		@Test
		@DisplayName("팔로워 수 내림차순이다")
		void findPopularUsers_ordersByFollowerCountDesc() {
			User low = persistUserWithFollowers("low@tenure.app", "Low", 1);
			User high = persistUserWithFollowers("high@tenure.app", "High", 10);

			assertThat(recommendationRepository.findPopularUsers(viewer.getId(), null, SIZE))
					.extracting(User::getId).containsSubsequence(high.getId(), low.getId());
		}

		@Test
		@DisplayName("차단한·차단당한 사용자는 양방향으로 제외된다")
		void findPopularUsers_blocked_excludedBothWays() {
			User blockedByMe = persistUser("blockedbyme@tenure.app", "BlockedByMe");
			User blocksMe = persistUser("blocksme@tenure.app", "BlocksMe");
			block(viewer, blockedByMe);
			block(blocksMe, viewer);

			List<Long> ids = recommendationRepository.findPopularUsers(viewer.getId(), null, SIZE)
					.stream().map(User::getId).toList();

			assertThat(ids).doesNotContain(blockedByMe.getId(), blocksMe.getId());
		}
	}

	private User persistUser(String email, String nickname) {
		User user = User.signUp(email, "$2a$10$hashed", "홍길동",
				"821012345678", nickname, null, Gender.MALE, 170, 64);
		em.persist(user);
		flush();
		return user;
	}

	private User persistUserWithFollowers(String email, String nickname, int followerCount) {
		User user = persistUser(email, nickname);
		em.createNativeQuery("update user set follower_count = ? where id = ?")
				.setParameter(1, followerCount)
				.setParameter(2, user.getId())
				.executeUpdate();
		em.clear();
		return user;
	}

	private Post persistPost(User postAuthor) {
		Post post = Post.publish(postAuthor, LocalDate.of(2026, 6, 1), AspectRatio.RATIO_3_4, false);
		em.persist(post);
		flush();
		return post;
	}

	private Item persistItem(Long categoryId) {
		Category category = em.find(Category.class, categoryId);
		Item item = Item.register(author, category, "Item", "Brand", "L",
				WearTarget.UNISEX, "item/2026/09/a.jpg", LocalDate.of(2025, 2, 8));
		em.persist(item);
		flush();
		return item;
	}

	private void tag(Post post, Item item) {
		PostImage image = PostImage.of(post, "post/2026/06/a.jpg", 0, true);
		em.persist(image);
		em.persist(PostItemTag.of(image, item, new BigDecimal("0.4200"), new BigDecimal("0.6100")));
		flush();
	}

	private void block(User blocker, User blocked) {
		em.createNativeQuery("insert into user_block (blocker_id, blocked_id, created_at) "
						+ "values (?, ?, now(6))")
				.setParameter(1, blocker.getId())
				.setParameter(2, blocked.getId())
				.executeUpdate();
	}

	private void setLikeCount(Post post, int value) {
		em.createNativeQuery("update post set like_count = ? where id = ?")
				.setParameter(1, value)
				.setParameter(2, post.getId())
				.executeUpdate();
		em.clear();
	}

	private void backdateCreatedAt(Post post, LocalDateTime createdAt) {
		em.createNativeQuery("update post set created_at = ? where id = ?")
				.setParameter(1, createdAt)
				.setParameter(2, post.getId())
				.executeUpdate();
		em.clear();
	}

	private void flush() {
		em.flush();
	}
}
