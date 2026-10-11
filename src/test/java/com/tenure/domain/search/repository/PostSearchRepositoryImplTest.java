package com.tenure.domain.search.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.entity.PostImage;
import com.tenure.domain.post.entity.PostItemTag;
import com.tenure.domain.post.enums.AspectRatio;
import com.tenure.domain.search.dto.PostSearchCriteria;
import com.tenure.domain.search.enums.AuthorGenderFilter;
import com.tenure.domain.search.enums.PostSortType;
import com.tenure.domain.search.enums.SaleStatusFilter;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.global.pagination.Cursor;
import com.tenure.support.RepositorySliceTestSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 게시물 검색(API-200). 키워드 매칭 등급·필터 조합·차단 제외·정렬 5종을 실제 MySQL 에서 확인한다.
 *
 * 세부 카테고리 id 는 V2 시드 값을 그대로 쓴다 — 101 블루종(부모 1 아우터),
 * 115 반팔 티셔츠(부모 2 상의), 127 코튼 팬츠(부모 3 하의), 128 데님 팬츠(부모 3 하의).
 */
@DisplayName("게시물 검색 쿼리")
@Import(PostSearchRepositoryImpl.class)
class PostSearchRepositoryImplTest extends RepositorySliceTestSupport {

	private static final int SIZE = 20;

	@Autowired private EntityManager em;
	@Autowired private PostSearchRepository postSearchRepository;

	private User viewer;
	private User author;

	@BeforeEach
	void setUp() {
		viewer = persistUser("viewer@tenure.app", "Viewer", Gender.MALE, 170, 64);
		author = persistUser("author@tenure.app", "Author", Gender.MALE, 170, 64);
	}

	@Nested
	@DisplayName("키워드 매칭 등급")
	class MatchGrade {

		@Test
		@DisplayName("브랜드+이름이 둘 다 일치(3등급)하면 하나만 일치(2등급)보다 앞선다 — 등급0은 아예 빠진다")
		void search_recommended_ordersGrade3BeforeGrade2AndExcludesGrade0() {
			Item bothMatch = persistItem(101L, "Levis", "Levis 501");
			Item brandOnly = persistItem(115L, "Levis", "Trucker Jacket");
			Item noMatch = persistItem(115L, "Uniqlo", "Basic Tee");

			Post grade3Post = persistPost();
			tag(grade3Post, bothMatch);
			Post grade2Post = persistPost();
			tag(grade2Post, brandOnly);
			Post grade0Post = persistPost();
			tag(grade0Post, noMatch);

			List<PostSearchRow> rows = search(criteria("Levis", PostSortType.RECOMMENDED));

			assertThat(rows).extracting(r -> r.post().getId())
					.containsExactly(grade3Post.getId(), grade2Post.getId());
		}

		@Test
		@DisplayName("카테고리명(1등급)은 세부 카테고리명으로도, 그 대분류명으로도 찾힌다")
		void search_categoryKeyword_matchesByOwnNameAndParentName() {
			Item item = persistItem(101L, "Uniqlo", "Basic");
			Post post = persistPost();
			tag(post, item);

			assertThat(search(criteria("블루종", PostSortType.LATEST)))
					.extracting(r -> r.post().getId()).containsExactly(post.getId());
			assertThat(search(criteria("아우터", PostSortType.LATEST)))
					.extracting(r -> r.post().getId()).containsExactly(post.getId());
		}

		@Test
		@DisplayName("키워드가 없으면 필터링 없이 전부 통과한다")
		void search_blankKeyword_doesNotFilter() {
			Item item = persistItem(115L, "Uniqlo", "Basic Tee");
			Post post = persistPost();
			tag(post, item);

			assertThat(search(criteria("", PostSortType.LATEST)))
					.extracting(r -> r.post().getId()).contains(post.getId());
			assertThat(search(criteria(null, PostSortType.LATEST)))
					.extracting(r -> r.post().getId()).contains(post.getId());
		}

		@Test
		@DisplayName("추천 순 복합 키는 등급을 먼저 보고, 같은 등급 안에서만 rank_score 로 가른다")
		void search_recommended_gradeAlwaysOutranksRankScore() {
			Item highGradeLowScore = persistItem(101L, "Levis", "Levis 501"); // 3등급
			Item lowGradeHighScore = persistItem(115L, "Levis", "Shirt"); // 2등급 (브랜드만 일치)

			Post grade3Post = persistPost();
			tag(grade3Post, highGradeLowScore);
			setRankScore(grade3Post, new BigDecimal("1.0000"));

			Post grade2Post = persistPost();
			tag(grade2Post, lowGradeHighScore);
			setRankScore(grade2Post, new BigDecimal("99.9999"));

			List<PostSearchRow> rows = search(criteria("Levis", PostSortType.RECOMMENDED));

			// grade2Post 의 rank_score 가 훨씬 높아도 등급이 낮아 뒤로 간다
			assertThat(rows).extracting(r -> r.post().getId())
					.containsExactly(grade3Post.getId(), grade2Post.getId());
		}
	}

	@Nested
	@DisplayName("판매 상태 필터")
	class SaleStatusFilterTests {

		@Test
		@DisplayName("ALL 은 판매 상태와 무관하게 전부 포함한다")
		void search_all_includesEveryStatus() {
			Post onSale = postWithItemStatus(SaleStatus.ON_SALE);
			Post sold = postWithItemStatus(SaleStatus.SOLD);
			Post notForSale = postWithItemStatus(SaleStatus.NOT_FOR_SALE);

			assertThat(search(criteria(null, SaleStatusFilter.ALL)))
					.extracting(r -> r.post().getId())
					.containsExactlyInAnyOrder(onSale.getId(), sold.getId(), notForSale.getId());
		}

		@Test
		@DisplayName("판매중 포함(INCLUDE_ON_SALE) 은 판매완료만 제외한다")
		void search_includeOnSale_excludesOnlySold() {
			Post onSale = postWithItemStatus(SaleStatus.ON_SALE);
			Post sold = postWithItemStatus(SaleStatus.SOLD);
			Post notForSale = postWithItemStatus(SaleStatus.NOT_FOR_SALE);

			assertThat(search(criteria(null, SaleStatusFilter.INCLUDE_ON_SALE)))
					.extracting(r -> r.post().getId())
					.containsExactlyInAnyOrder(onSale.getId(), notForSale.getId())
					.doesNotContain(sold.getId());
		}

		@Test
		@DisplayName("판매중만(ONLY_ON_SALE) 은 판매중인 아이템이 태그된 것만 남긴다")
		void search_onlyOnSale_keepsOnSaleOnly() {
			Post onSale = postWithItemStatus(SaleStatus.ON_SALE);
			postWithItemStatus(SaleStatus.SOLD);
			postWithItemStatus(SaleStatus.NOT_FOR_SALE);

			assertThat(search(criteria(null, SaleStatusFilter.ONLY_ON_SALE)))
					.extracting(r -> r.post().getId())
					.containsExactly(onSale.getId());
		}

		@Test
		@DisplayName("태그 중 하나라도 조건을 만족하면 포함된다 — 전부가 아니라 EXISTS 다")
		void search_onlyOnSale_anyMatchingTagIsEnough() {
			Item onSaleItem = persistItem(115L, "A", "A");
			onSaleItem.changeSaleStatus(SaleStatus.ON_SALE);
			Item soldItem = persistItem(115L, "B", "B");
			soldItem.changeSaleStatus(SaleStatus.SOLD);
			Post mixed = persistPost();
			PostImage image = tag(mixed, onSaleItem);
			em.persist(PostItemTag.of(image, soldItem, new BigDecimal("0.1000"), new BigDecimal("0.1000")));
			flush();

			assertThat(search(criteria(null, SaleStatusFilter.ONLY_ON_SALE)))
					.extracting(r -> r.post().getId()).contains(mixed.getId());
		}

		private Post postWithItemStatus(SaleStatus status) {
			Item item = persistItem(115L, "Brand" + status, "Item" + status);
			item.changeSaleStatus(status);
			Post post = persistPost();
			tag(post, item);
			return post;
		}
	}

	@Nested
	@DisplayName("게시자 속성·카테고리 필터")
	class AuthorAndCategoryFilters {

		@Test
		@DisplayName("게시자 성별 필터는 그 성별 작성자의 게시물만 남긴다")
		void search_authorGender_filtersByAuthorGender() {
			User maleAuthor = persistUser("male@tenure.app", "Male", Gender.MALE, 170, 64);
			User femaleAuthor = persistUser("female@tenure.app", "Female", Gender.FEMALE, 160, 50);
			Post malePost = persistPost(maleAuthor);
			Post femalePost = persistPost(femaleAuthor);

			List<PostSearchRow> rows = search(new PostSearchCriteria(
					null, null, AuthorGenderFilter.MALE, null, null, null, null, null, PostSortType.LATEST));

			assertThat(rows).extracting(r -> r.post().getId())
					.contains(malePost.getId()).doesNotContain(femalePost.getId());
		}

		@Test
		@DisplayName("키·몸무게 범위 밖의 작성자 게시물은 빠지고, 값이 없는 작성자도 빠진다")
		void search_heightWeightRange_excludesOutOfRangeAndNullAuthors() {
			User tall = persistUser("tall@tenure.app", "Tall", Gender.MALE, 190, 80);
			User mid = persistUser("mid@tenure.app", "Mid", Gender.MALE, 175, 70);
			User noPhysique = persistUser("none@tenure.app", "None", Gender.MALE, null, null);
			Post tallPost = persistPost(tall);
			Post midPost = persistPost(mid);
			Post noPhysiquePost = persistPost(noPhysique);

			List<PostSearchRow> rows = search(new PostSearchCriteria(
					null, null, null, null, 170, 180, null, null, PostSortType.LATEST));

			assertThat(rows).extracting(r -> r.post().getId())
					.contains(midPost.getId())
					.doesNotContain(tallPost.getId(), noPhysiquePost.getId());
		}

		@Test
		@DisplayName("세부 카테고리를 여럿 고르면 그중 하나라도 태그된 게시물만 남긴다")
		void search_categoryIds_filtersByAnyOfSelectedCategories() {
			Item matching = persistItem(101L, "A", "A");
			Item other = persistItem(128L, "B", "B");
			Post matchingPost = persistPost();
			tag(matchingPost, matching);
			Post otherPost = persistPost();
			tag(otherPost, other);

			List<PostSearchRow> rows = search(new PostSearchCriteria(
					null, null, null, List.of(101L, 115L), null, null, null, null, PostSortType.LATEST));

			assertThat(rows).extracting(r -> r.post().getId())
					.contains(matchingPost.getId()).doesNotContain(otherPost.getId());
		}
	}

	@Nested
	@DisplayName("차단 관계 제외")
	class Block {

		@Test
		@DisplayName("내가 차단한 작성자의 게시물은 보이지 않는다")
		void search_blockedByViewer_excludesAuthor() {
			User blocked = persistUser("blocked@tenure.app", "Blocked", Gender.MALE, 170, 64);
			persistPost(blocked);
			block(viewer, blocked);

			assertThat(search(criteria(null, PostSortType.LATEST))).isEmpty();
		}

		@Test
		@DisplayName("나를 차단한 작성자의 게시물도 보이지 않는다")
		void search_blockedViewer_excludesAuthor() {
			User blocker = persistUser("blocker@tenure.app", "Blocker", Gender.MALE, 170, 64);
			persistPost(blocker);
			block(blocker, viewer);

			assertThat(search(criteria(null, PostSortType.LATEST))).isEmpty();
		}
	}

	@Nested
	@DisplayName("정렬")
	class Sorting {

		@Test
		@DisplayName("최신순은 게시일 내림차순이다")
		void search_latest_ordersByCreatedAtDesc() {
			Post older = persistPost();
			Post newer = persistPost();
			backdateCreatedAt(older, java.time.LocalDateTime.now().minusDays(2));
			backdateCreatedAt(newer, java.time.LocalDateTime.now().minusDays(1));

			assertThat(search(criteria(null, PostSortType.LATEST)))
					.extracting(r -> r.post().getId())
					.containsExactly(newer.getId(), older.getId());
		}

		@Test
		@DisplayName("좋아요·조회수·저장수 순은 각 카운터 내림차순이다")
		void search_countSorts_orderByEachCounterDesc() {
			Post low = persistPost();
			Post high = persistPost();
			setCount(low, "like_count", 1);
			setCount(high, "like_count", 10);
			setCount(low, "view_count", 1);
			setCount(high, "view_count", 10);
			setCount(low, "save_count", 1);
			setCount(high, "save_count", 10);

			assertThat(search(criteria(null, PostSortType.LIKE)))
					.extracting(r -> r.post().getId()).containsExactly(high.getId(), low.getId());
			assertThat(search(criteria(null, PostSortType.VIEW)))
					.extracting(r -> r.post().getId()).containsExactly(high.getId(), low.getId());
			assertThat(search(criteria(null, PostSortType.SAVE)))
					.extracting(r -> r.post().getId()).containsExactly(high.getId(), low.getId());
		}

		@Test
		@DisplayName("추천 순 페이지를 연속으로 넘겨도 중복·누락이 없다")
		void search_recommended_pagingHasNoDuplicatesOrGaps() {
			List<Long> expectedOrder = new ArrayList<>();
			BigDecimal score = new BigDecimal("10.0000");
			for (int i = 0; i < 5; i++) {
				Post post = persistPost();
				setRankScore(post, score);
				expectedOrder.add(post.getId());
				score = score.subtract(BigDecimal.ONE);
			}

			List<Long> collected = new ArrayList<>();
			Cursor cursor = null;
			for (int i = 0; i < 5; i++) {
				List<PostSearchRow> page = postSearchRepository.search(
						criteria(null, PostSortType.RECOMMENDED), viewer.getId(), cursor, 2);
				int take = Math.min(2, page.size());
				for (int j = 0; j < take; j++) {
					collected.add(page.get(j).post().getId());
				}
				if (page.isEmpty()) {
					break;
				}
				cursor = page.get(take - 1).cursor();
			}

			assertThat(collected).containsExactlyElementsOf(expectedOrder);
		}
	}

	@Nested
	@DisplayName("총 건수 집계")
	class CountMatches {

		@Test
		@DisplayName("페이지 크기와 무관하게 조건에 맞는 전체 건수를 돌려준다")
		void countMatches_countsAllMatchingRegardlessOfPageSize() {
			persistPost();
			persistPost();
			persistPost();

			long count = postSearchRepository.countMatches(criteria(null, PostSortType.LATEST), viewer.getId());

			assertThat(count).isEqualTo(3);
		}
	}

	private List<PostSearchRow> search(PostSearchCriteria criteria) {
		return postSearchRepository.search(criteria, viewer.getId(), null, SIZE);
	}

	private PostSearchCriteria criteria(String keyword, PostSortType sort) {
		return new PostSearchCriteria(keyword, null, null, null, null, null, null, null, sort);
	}

	private PostSearchCriteria criteria(String keyword, SaleStatusFilter saleStatus) {
		return new PostSearchCriteria(keyword, saleStatus, null, null, null, null, null, null, PostSortType.LATEST);
	}

	private User persistUser(String email, String nickname, Gender gender, Integer height, Integer weight) {
		User user = User.signUp(email, "$2a$10$hashed", "홍길동",
				"821012345678", nickname, null, gender, height, weight);
		em.persist(user);
		flush();
		return user;
	}

	private Post persistPost() {
		return persistPost(author);
	}

	private Post persistPost(User postAuthor) {
		Post post = Post.publish(postAuthor, LocalDate.of(2026, 6, 1), AspectRatio.RATIO_3_4, false);
		em.persist(post);
		flush();
		return post;
	}

	private Item persistItem(Long categoryId, String brandName, String name) {
		Category category = em.find(Category.class, categoryId);
		Item item = Item.register(author, category, name, brandName, "L",
				WearTarget.UNISEX, "item/2026/09/a.jpg", LocalDate.of(2025, 2, 8));
		em.persist(item);
		flush();
		return item;
	}

	private PostImage tag(Post post, Item item) {
		PostImage image = PostImage.of(post, "post/2026/06/a.jpg", 0, true);
		em.persist(image);
		em.persist(PostItemTag.of(image, item, new BigDecimal("0.4200"), new BigDecimal("0.6100")));
		flush();
		return image;
	}

	private void block(User blocker, User blocked) {
		em.createNativeQuery("insert into user_block (blocker_id, blocked_id, created_at) "
						+ "values (?, ?, now(6))")
				.setParameter(1, blocker.getId())
				.setParameter(2, blocked.getId())
				.executeUpdate();
	}

	private void setRankScore(Post post, BigDecimal score) {
		em.createNativeQuery("update post set rank_score = ? where id = ?")
				.setParameter(1, score)
				.setParameter(2, post.getId())
				.executeUpdate();
		em.clear();
	}

	private void setCount(Post post, String column, int value) {
		em.createNativeQuery("update post set " + column + " = ? where id = ?")
				.setParameter(1, value)
				.setParameter(2, post.getId())
				.executeUpdate();
		em.clear();
	}

	private void backdateCreatedAt(Post post, java.time.LocalDateTime createdAt) {
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
