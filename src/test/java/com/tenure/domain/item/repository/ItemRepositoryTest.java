package com.tenure.domain.item.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.category.enums.SizeScheme;
import com.tenure.domain.category.repository.CategoryRepository;
import com.tenure.domain.category.repository.SizeOptionRepository;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.RecommendationBlock;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.entity.PostImage;
import com.tenure.domain.post.entity.PostItemTag;
import com.tenure.domain.post.enums.AspectRatio;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.support.RepositorySliceTestSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 아이템 조회 쿼리.
 *
 * H2 가 아니라 실제 MySQL 에서 돈다. 소유 구간 exists 와 추천의 중첩 서브쿼리는 방언에 따라
 * 결과가 갈릴 수 있어 여기서 확인해 둔다. 사이즈 칩 seed 도 함께 본다 — 값이 빠지면
 * 등록이 400 으로 막힌다.
 */
@DisplayName("아이템 조회 쿼리")
class ItemRepositoryTest extends RepositorySliceTestSupport {

	private static final int SIZE = 20;

	/** V2 시드의 고정 id — 상의 2, 긴소매 티셔츠 116, 아우터 1, 블루종 101 */
	private static final Long TOP_SHIRT = 116L;
	private static final Long OUTER_BLOUSON = 101L;

	@Autowired private EntityManager em;
	@Autowired private ItemRepository itemRepository;
	@Autowired private CategoryRepository categoryRepository;
	@Autowired private SizeOptionRepository sizeOptionRepository;

	private User me;
	private User other;

	@BeforeEach
	void setUp() {
		me = persistUser("me@tenure.app", "Sujun");
		other = persistUser("other@tenure.app", "YuJin");
	}

	@Nested
	@DisplayName("내 옷장 목록")
	class FindOwnedItems {

		@Test
		@DisplayName("현재 소유 구간이 열려 있는 아이템만 담는다 — 판 아이템은 빠진다")
		void findOwnedItems_onlyCurrentlyOwned() {
			Item mine = persistItem(me, TOP_SHIRT, "LVC 1955 501");
			persistOwnership(mine, me, null);

			Item sold = persistItem(me, TOP_SHIRT, "Third Cut");
			persistOwnership(sold, me, LocalDate.of(2026, 7, 10));
			persistOwnership(sold, other, null);

			List<Item> items = itemRepository.findOwnedItems(me.getId(), null, null, SIZE);

			assertThat(items).extracting(Item::getId).containsExactly(mine.getId());
		}

		@Test
		@DisplayName("삭제한 아이템은 빠진다")
		void findOwnedItems_excludesDeleted() {
			Item deleted = persistItem(me, TOP_SHIRT, "지운 것");
			persistOwnership(deleted, me, null);
			deleted.delete(java.time.LocalDateTime.now());
			flush();

			assertThat(itemRepository.findOwnedItems(me.getId(), null, null, SIZE)).isEmpty();
		}

		@Test
		@DisplayName("검색어는 브랜드명과 아이템명 어느 쪽에 걸려도 잡는다")
		void findOwnedItems_keywordMatchesBrandOrName() {
			Item levis = persistItem(me, TOP_SHIRT, "LVC 1955 501");
			persistOwnership(levis, me, null);

			assertThat(itemRepository.findOwnedItems(me.getId(), "Levis", null, SIZE))
					.extracting(Item::getId).containsExactly(levis.getId());
			assertThat(itemRepository.findOwnedItems(me.getId(), "1955", null, SIZE))
					.extracting(Item::getId).containsExactly(levis.getId());
			assertThat(itemRepository.findOwnedItems(me.getId(), "Nike", null, SIZE)).isEmpty();
		}

		@Test
		@DisplayName("남의 아이템은 섞이지 않는다")
		void findOwnedItems_excludesOthers() {
			Item theirs = persistItem(other, TOP_SHIRT, "남의 것");
			persistOwnership(theirs, other, null);

			assertThat(itemRepository.findOwnedItems(me.getId(), null, null, SIZE)).isEmpty();
		}
	}

	@Nested
	@DisplayName("아이템 기준 추천")
	class FindRecommendedPosts {

		@Test
		@DisplayName("같은 세부 카테고리의 다른 아이템이 태그된 게시물을 모은다")
		void similarItem_sameCategory() {
			Item base = persistItem(me, TOP_SHIRT, "기준");
			Item sameCategory = persistItem(other, TOP_SHIRT, "같은 세부 카테고리");
			Item otherCategory = persistItem(other, OUTER_BLOUSON, "다른 카테고리");

			Post hit = persistPostWithTag(other, sameCategory);
			persistPostWithTag(other, otherCategory);

			List<Post> posts = itemRepository.findRecommendedPosts(
					me.getId(), base, RecommendationBlock.SIMILAR_ITEM, 10);

			assertThat(posts).extracting(Post::getId).containsExactly(hit.getId());
		}

		@Test
		@DisplayName("대분류가 같으면 세부 카테고리가 달라도 담는다")
		void similarMood_sameRootCategory() {
			Item base = persistItem(me, TOP_SHIRT, "기준");
			// 115 는 반팔 티셔츠 — 116 과 대분류(상의)가 같다
			Item sameRoot = persistItem(other, 115L, "같은 대분류");

			Post hit = persistPostWithTag(other, sameRoot);

			List<Post> posts = itemRepository.findRecommendedPosts(
					me.getId(), base, RecommendationBlock.SIMILAR_MOOD, 10);

			assertThat(posts).extracting(Post::getId).containsExactly(hit.getId());
		}

		@Test
		@DisplayName("차단한 사용자의 게시물은 빠진다")
		void recommendation_excludesBlocked() {
			Item base = persistItem(me, TOP_SHIRT, "기준");
			Item sameCategory = persistItem(other, TOP_SHIRT, "같은 세부 카테고리");
			persistPostWithTag(other, sameCategory);
			block(me, other);

			assertThat(itemRepository.findRecommendedPosts(
					me.getId(), base, RecommendationBlock.SIMILAR_ITEM, 10)).isEmpty();
		}

		@Test
		@DisplayName("기준 아이템 자신만 태그된 게시물은 담지 않는다")
		void recommendation_excludesSelfOnly() {
			Item base = persistItem(me, TOP_SHIRT, "기준");
			persistPostWithTag(me, base);

			assertThat(itemRepository.findRecommendedPosts(
					me.getId(), base, RecommendationBlock.SIMILAR_ITEM, 10)).isEmpty();
		}
	}

	@Nested
	@DisplayName("사이즈 칩 seed")
	class SizeOptions {

		@Test
		@DisplayName("대분류마다 칩 세트가 붙어 있다 — 비면 사이즈를 고를 수 없다")
		void categories_haveSizeScheme() {
			assertThat(categoryRepository.findById(2L)).get()
					.extracting(Category::getSizeScheme).isEqualTo(SizeScheme.TOP);
			assertThat(categoryRepository.findById(3L)).get()
					.extracting(Category::getSizeScheme).isEqualTo(SizeScheme.BOTTOM);
			assertThat(categoryRepository.findById(4L)).get()
					.extracting(Category::getSizeScheme).isEqualTo(SizeScheme.SHOE);
			assertThat(categoryRepository.findById(6L)).get()
					.extracting(Category::getSizeScheme).isEqualTo(SizeScheme.ONE_SIZE);
		}

		@Test
		@DisplayName("세부 카테고리는 비어 있다 — 부모에서 읽는다")
		void leafCategories_haveNoScheme() {
			assertThat(categoryRepository.findById(TOP_SHIRT)).get()
					.extracting(Category::getSizeScheme).isNull();
		}

		@Test
		@DisplayName("화면에서 읽은 칩이 그대로 들어 있다")
		void sizeOptions_matchScreens() {
			assertThat(sizeOptionRepository.findLabels(SizeScheme.TOP))
					.startsWith("XS", "S", "M", "L", "XL", "2XL", "3XL")
					.contains("85", "115", "36", "50", "0", "6")
					.hasSize(29);

			// 하의는 인치라 24~40 이 붙는다. 36 은 상의의 EU 사이즈이기도 해서 값만으로는 갈리지 않는다
			assertThat(sizeOptionRepository.findLabels(SizeScheme.BOTTOM))
					.contains("24", "36", "40").doesNotContain("85", "50")
					.hasSize(31);

			assertThat(sizeOptionRepository.findLabels(SizeScheme.SHOE))
					.startsWith("210").endsWith("310").hasSize(21);

			assertThat(sizeOptionRepository.findLabels(SizeScheme.ONE_SIZE))
					.containsExactly("One size");
		}

		@Test
		@DisplayName("대소문자를 구분하지 않는다 — collation 이 무시한다")
		void existsBySchemeAndLabel_ignoresCase() {
			assertThat(sizeOptionRepository.existsBySchemeAndLabel(SizeScheme.TOP, "l")).isTrue();
			assertThat(sizeOptionRepository.existsBySchemeAndLabel(SizeScheme.TOP, "999XL")).isFalse();
			// 하의 칩 세트에 없는 값은 막힌다
			assertThat(sizeOptionRepository.existsBySchemeAndLabel(SizeScheme.BOTTOM, "85")).isFalse();
		}
	}

	private User persistUser(String email, String nickname) {
		User user = User.signUp(email, "$2a$10$hashed", "홍길동",
				"821012345678", nickname, null, Gender.MALE, 170, 64);
		em.persist(user);
		flush();
		return user;
	}

	private Item persistItem(User registrant, Long categoryId, String name) {
		Category category = em.find(Category.class, categoryId);
		Item item = Item.register(registrant, category, name, "Levis", "L",
				WearTarget.UNISEX, "item/2026/09/a.jpg", null, LocalDate.of(2025, 2, 8));
		em.persist(item);
		flush();
		return item;
	}

	private void persistOwnership(Item item, User owner, LocalDate endDate) {
		ItemOwnership ownership = ItemOwnership.openInitial(item, owner, LocalDate.of(2025, 2, 8));
		if (endDate != null) {
			ownership.close(endDate);
		}
		em.persist(ownership);
		flush();
	}

	private Post persistPostWithTag(User author, Item item) {
		Post post = Post.publish(author, LocalDate.of(2026, 6, 1), AspectRatio.RATIO_3_4, false);
		em.persist(post);

		PostImage image = PostImage.of(post, "post/2026/06/a.jpg", 0, true);
		em.persist(image);
		em.persist(PostItemTag.of(image, item, new BigDecimal("0.4200"), new BigDecimal("0.6100")));
		flush();
		return post;
	}

	private void flush() {
		em.flush();
	}

	private void block(User blocker, User blocked) {
		em.createNativeQuery("insert into user_block (blocker_id, blocked_id, created_at) "
						+ "values (?, ?, now(6))")
				.setParameter(1, blocker.getId())
				.setParameter(2, blocked.getId())
				.executeUpdate();
		flush();
	}
}
