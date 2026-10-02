package com.tenure.domain.listing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.category.enums.MeasurementScheme;
import com.tenure.domain.category.repository.CategoryRepository;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.listing.entity.ListingFeaturedPost;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.entity.PostImage;
import com.tenure.domain.post.entity.PostItemTag;
import com.tenure.domain.post.enums.AspectRatio;
import com.tenure.domain.post.repository.PostItemTagRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
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
import org.springframework.data.domain.Pageable;

/**
 * 판매 등록 조회 쿼리.
 *
 * H2 가 아니라 실제 MySQL 에서 돈다. '열린 행 하나' 를 찾는 조회가 가장 잦으므로
 * 닫힌 행이 섞여도 제대로 걸러지는지 확인해 둔다.
 *
 * measurement_scheme seed 도 함께 본다 — 값이 빠지면 판매 전환이 400 으로 막힌다.
 */
@DisplayName("판매 등록 조회 쿼리")
class ListingRepositoryTest extends RepositorySliceTestSupport {

	/** V2 시드의 고정 id — 상의 2, 긴소매 티셔츠 116, 하의 3, 치마 7, 신발 4 */
	private static final Long TOP_SHIRT = 116L;
	private static final Long TOP = 2L;
	private static final Long BOTTOM = 3L;
	private static final Long SKIRT = 7L;
	private static final Long SHOE = 4L;

	@Autowired private EntityManager em;
	@Autowired private ItemListingRepository itemListingRepository;
	@Autowired private ListingFeaturedPostRepository listingFeaturedPostRepository;
	@Autowired private PostItemTagRepository postItemTagRepository;
	@Autowired private CategoryRepository categoryRepository;

	private User me;

	@BeforeEach
	void setUp() {
		me = persistUser("me@tenure.app", "Sujun");
	}

	@Nested
	@DisplayName("열린 판매 등록")
	class OpenListing {

		@Test
		@DisplayName("닫힌 행이 쌓여 있어도 열린 것만 돌려준다")
		void findOpenByItemId_skipsClosed() {
			Item item = persistItem("LVC 1955 501");
			persistListing(item, 300000, true);
			ItemListing open = persistListing(item, 360000, false);

			assertThat(itemListingRepository.findOpenByItemId(item.getId()))
					.get()
					.extracting(ItemListing::getId)
					.isEqualTo(open.getId());
		}

		@Test
		@DisplayName("전부 닫혔으면 비어 있다 — 미판매로 내린 상태다")
		void findOpenByItemId_allClosed_empty() {
			Item item = persistItem("LVC 1955 501");
			persistListing(item, 300000, true);

			assertThat(itemListingRepository.findOpenByItemId(item.getId())).isEmpty();
		}

		@Test
		@DisplayName("여러 아이템의 열린 행을 한 번에 받는다")
		void findOpenByItemIds_batches() {
			Item a = persistItem("A");
			Item b = persistItem("B");
			ItemListing openA = persistListing(a, 100000, false);
			persistListing(b, 200000, true);

			assertThat(itemListingRepository.findOpenByItemIds(List.of(a.getId(), b.getId())))
					.extracting(ItemListing::getId)
					.containsExactly(openA.getId());
		}

		@Test
		@DisplayName("열린 행은 늘 최신이다 — 조회가 최신 한 건만 읽고 열림 여부를 가린다")
		void findLatestByItemId_openRowIsAlwaysLatest() {
			Item item = persistItem("LVC 1955 501");
			persistListing(item, 300000, true);
			ItemListing open = persistListing(item, 360000, false);

			ItemListing latest = itemListingRepository.findLatestByItemId(item.getId()).orElseThrow();

			assertThat(latest.getId()).isEqualTo(open.getId());
			assertThat(latest.isOpen()).isTrue();
		}

		@Test
		@DisplayName("전부 닫혔으면 최신 행이 닫힌 것으로 나온다 — 조건 섹션이 접힌다")
		void findLatestByItemId_allClosed_latestIsClosed() {
			Item item = persistItem("LVC 1955 501");
			persistListing(item, 300000, true);
			ItemListing lastClosed = persistListing(item, 360000, true);

			ItemListing latest = itemListingRepository.findLatestByItemId(item.getId()).orElseThrow();

			assertThat(latest.getId()).isEqualTo(lastClosed.getId());
			assertThat(latest.isOpen()).isFalse();
		}

		@Test
		@DisplayName("최신 행은 닫힌 것도 포함한다 — 판매자 정보와 폼 프리필이 이 값을 쓴다")
		void findLatestByItemId_includesClosed() {
			Item item = persistItem("LVC 1955 501");
			persistListing(item, 300000, true);
			ItemListing latest = persistListing(item, 360000, true);

			assertThat(itemListingRepository.findLatestByItemId(item.getId()))
					.get()
					.extracting(ItemListing::getId)
					.isEqualTo(latest.getId());
		}
	}

	@Nested
	@DisplayName("대표 OOTD")
	class FeaturedPosts {

		@Test
		@DisplayName("선택 순서대로 돌려준다 — 상세의 가로 스크롤이 그 순서를 따른다")
		void findAllByListingId_ordersBySortOrder() {
			Item item = persistItem("LVC 1955 501");
			ItemListing listing = persistListing(item, 360000, false);

			Post first = persistPost(LocalDate.of(2026, 6, 1));
			Post second = persistPost(LocalDate.of(2026, 6, 2));

			em.persist(ListingFeaturedPost.of(listing, second, 1));
			em.persist(ListingFeaturedPost.of(listing, first, 0));
			flush();

			assertThat(listingFeaturedPostRepository.findAllByListingId(listing.getId()))
					.extracting(row -> row.getPost().getId())
					.containsExactly(first.getId(), second.getId());
		}

		@Test
		@DisplayName("전체 교체 전에 비운다")
		void deleteAllByListingId_clears() {
			Item item = persistItem("LVC 1955 501");
			ItemListing listing = persistListing(item, 360000, false);
			em.persist(ListingFeaturedPost.of(listing, persistPost(LocalDate.of(2026, 6, 1)), 0));
			flush();

			listingFeaturedPostRepository.deleteAllByListingId(listing.getId());

			assertThat(listingFeaturedPostRepository.findAllByListingId(listing.getId())).isEmpty();
		}
	}

	@Nested
	@DisplayName("태그된 OOTD")
	class TaggedPosts {

		@Test
		@DisplayName("한 게시물의 사진 여러 장에 같은 아이템이 태그돼도 한 번만 센다")
		void findTaggedPosts_distinctByPost() {
			Item item = persistItem("LVC 1955 501");
			Post post = persistPost(LocalDate.of(2026, 6, 1));

			PostImage first = PostImage.of(post, "post/2026/06/a.jpg", 0, true);
			PostImage second = PostImage.of(post, "post/2026/06/b.jpg", 1, false);
			em.persist(first);
			em.persist(second);
			em.persist(PostItemTag.of(first, item, new BigDecimal("0.4200"), new BigDecimal("0.6100")));
			em.persist(PostItemTag.of(second, item, new BigDecimal("0.3000"), new BigDecimal("0.5000")));
			flush();

			assertThat(postItemTagRepository.findTaggedPosts(item.getId(), Pageable.ofSize(10)))
					.extracting(Post::getId)
					.containsExactly(post.getId());
		}

		@Test
		@DisplayName("삭제된 게시물은 빠진다")
		void findTaggedPosts_excludesDeletedPost() {
			Item item = persistItem("LVC 1955 501");
			Post post = persistPost(LocalDate.of(2026, 6, 1));
			PostImage image = PostImage.of(post, "post/2026/06/a.jpg", 0, true);
			em.persist(image);
			em.persist(PostItemTag.of(image, item, new BigDecimal("0.4200"), new BigDecimal("0.6100")));
			post.softDelete();
			flush();

			assertThat(postItemTagRepository.findTaggedPosts(item.getId(), Pageable.ofSize(10)))
					.isEmpty();
		}
	}

	@Nested
	@DisplayName("실측 세트 시드")
	class MeasurementSchemeSeed {

		@Test
		@DisplayName("대분류마다 실측 세트가 채워져 있다 — 하의와 치마가 갈린다")
		void seed_assignsSchemePerRootCategory() {
			assertThat(schemeOf(TOP)).isEqualTo(MeasurementScheme.TOP);
			assertThat(schemeOf(BOTTOM)).isEqualTo(MeasurementScheme.BOTTOM);
			assertThat(schemeOf(SKIRT)).isEqualTo(MeasurementScheme.SKIRT);
			assertThat(schemeOf(SHOE)).isEqualTo(MeasurementScheme.NONE);
		}

		@Test
		@DisplayName("세부 카테고리는 비어 있다 — 부모에서 읽는다")
		void seed_leafHasNoScheme() {
			assertThat(schemeOf(TOP_SHIRT)).isNull();
		}

		private MeasurementScheme schemeOf(Long categoryId) {
			return categoryRepository.findById(categoryId).orElseThrow().getMeasurementScheme();
		}
	}

	// ── 픽스처

	private User persistUser(String email, String nickname) {
		User user = User.signUp(email, "$2a$10$hashed", "홍길동",
				"821012345678", nickname, null, Gender.MALE, 170, 64);
		em.persist(user);
		flush();
		return user;
	}

	private Item persistItem(String name) {
		Category category = em.find(Category.class, TOP_SHIRT);
		Item item = Item.register(me, category, name, "Levis", "L",
				WearTarget.UNISEX, "item/2026/09/a.jpg", LocalDate.of(2025, 2, 8));
		em.persist(item);
		flush();
		return item;
	}

	private ItemListing persistListing(Item item, int price, boolean closed) {
		ItemListing listing = ItemListing.open(
				item, me, price, 0, FeeBearer.SELLER, "설명", LocalDateTime.now());
		if (closed) {
			listing.close(LocalDateTime.now());
		}
		em.persist(listing);
		flush();
		return listing;
	}

	private void flush() {
		em.flush();
	}

	private Post persistPost(LocalDate wornDate) {
		Post post = Post.publish(me, wornDate, AspectRatio.RATIO_3_4, false);
		em.persist(post);
		flush();
		return post;
	}
}
