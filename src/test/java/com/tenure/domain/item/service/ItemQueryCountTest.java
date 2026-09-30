package com.tenure.domain.item.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.item.dto.request.ItemCreateRequest;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.entity.ItemWearRecord;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.item.repository.ItemWearRecordRepository;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.entity.PostImage;
import com.tenure.domain.post.entity.PostItemTag;
import com.tenure.domain.post.enums.AspectRatio;
import com.tenure.domain.post.repository.PostImageRepository;
import com.tenure.domain.post.repository.PostItemTagRepository;
import com.tenure.domain.post.repository.PostRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.support.IntegrationTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * 아이템 조회 경로에서 나가는 쿼리 수를 센다.
 *
 * 목록은 최근 착용일·판매 등록·인증 횟수를 아이템마다 조회하면 행 수만큼 쿼리가 붙는다(N+1).
 * 데이터를 늘려도 수치가 그대로인지를 실제로 못박아 둔다 — 늘어나면 어딘가에서 지연 로딩이 터진 것이다.
 */
@DisplayName("아이템 조회 쿼리 수")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ItemQueryCountTest extends IntegrationTestSupport {

	/** V2 시드의 고정 id — 긴소매 티셔츠 */
	private static final Long CATEGORY_ID = 116L;

	@Autowired private ItemService itemService;
	@Autowired private ItemQueryService itemQueryService;
	@Autowired private ItemRepository itemRepository;
	@Autowired private ItemOwnershipRepository itemOwnershipRepository;
	@Autowired private ItemWearRecordRepository itemWearRecordRepository;
	@Autowired private PostRepository postRepository;
	@Autowired private PostImageRepository postImageRepository;
	@Autowired private PostItemTagRepository postItemTagRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private EntityManagerFactory entityManagerFactory;

	private User owner;
	private Statistics statistics;
	private final List<Item> items = new ArrayList<>();
	private final List<Post> posts = new ArrayList<>();
	private final List<PostItemTag> tags = new ArrayList<>();

	@BeforeEach
	void setUp() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		owner = userRepository.save(User.signUp(
				"item-count-%s@tenure.local".formatted(tag), "$2a$10$hashed", "홍길동",
				"821012345678", "item-count-%s".formatted(tag), null, Gender.MALE, 170, 64));

		statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
	}

	@AfterEach
	void tearDown() {
		postItemTagRepository.deleteAll(tags);
		itemWearRecordRepository.deleteAll(itemWearRecordRepository.findAll().stream()
				.filter(record -> items.stream().anyMatch(item -> item.getId().equals(record.getItem().getId())))
				.toList());
		items.forEach(item -> itemOwnershipRepository.findCurrent(item.getId())
				.ifPresent(itemOwnershipRepository::delete));
		postImageRepository.deleteAll(postImageRepository.findAll().stream()
				.filter(image -> posts.stream().anyMatch(post -> post.getId().equals(image.getPost().getId())))
				.toList());
		postRepository.deleteAll(posts);
		itemRepository.deleteAll(items);
		userRepository.delete(owner);
	}

	@Test
	@DisplayName("내 옷장 목록은 아이템이 몇 건이든 쿼리 수가 같다")
	void getMyItems_manyItems_constantQueries() {
		for (int i = 0; i < 3; i++) {
			register("아이템" + i);
		}

		statistics.clear();
		assertThat(itemQueryService.getMyItems(owner.getId(), null, null, 20).getContent()).hasSize(3);
		long three = statistics.getPrepareStatementCount();

		for (int i = 3; i < 10; i++) {
			register("아이템" + i);
		}

		statistics.clear();
		assertThat(itemQueryService.getMyItems(owner.getId(), null, null, 20).getContent()).hasSize(10);

		// 3건과 10건이 같아야 한다. 늘어나면 행마다 조회하는 경로가 생긴 것이다
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(three);
	}

	@Test
	@DisplayName("태그 시트도 아이템 수와 무관하게 쿼리 수가 같다")
	void getTaggableItems_manyItems_constantQueries() {
		for (int i = 0; i < 3; i++) {
			register("태그" + i);
		}

		statistics.clear();
		itemQueryService.getTaggableItems(owner.getId(), null, null, 20);
		long three = statistics.getPrepareStatementCount();

		for (int i = 3; i < 10; i++) {
			register("태그" + i);
		}

		statistics.clear();
		itemQueryService.getTaggableItems(owner.getId(), null, null, 20);

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(three);
	}

	@Test
	@DisplayName("아이템 상세는 인증 기록이 늘어도 쿼리 수가 같다")
	void getItem_manyWearRecords_constantQueries() {
		Item item = register("상세");
		wear(item, 2);

		statistics.clear();
		itemQueryService.getItem(owner.getId(), item.getId());
		long few = statistics.getPrepareStatementCount();

		wear(item, 8);

		statistics.clear();
		itemQueryService.getItem(owner.getId(), item.getId());

		// 소유 구간 카드의 인증 횟수·미리보기를 구간마다 나눠 조회하면 여기서 늘어난다
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(few);
	}

	@Test
	@DisplayName("소유 구간 조회는 구간 수와 무관하게 3회다")
	void getOwnerships_fixedQueries() {
		Item item = register("기록");
		wear(item, 3);

		statistics.clear();
		itemQueryService.getOwnerships(item.getId());

		// 아이템 1 · 구간 목록 1 · 인증 횟수 1 · 미리보기 1 · 최근 착용일 1
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(5);
	}

	@Test
	@DisplayName("추천은 블록 수와 무관하게 카드 조립을 한 번만 한다")
	void getRecommendations_assemblesCardsOnce() {
		Item base = register("기준");
		Item peer = register("비교");
		tagTogether(base, peer, 3);

		statistics.clear();
		itemQueryService.getRecommendations(owner.getId(), base.getId());

		// 아이템 1 + 블록 3 + 카드 조립 3(대표 사진·좋아요·저장)
		// 블록마다 조립하면 1 + 3 + 3x3 = 13 이 된다
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(7);
	}

	@Test
	@DisplayName("아이템 상세는 소유 구간을 한 번만 읽는다")
	void getItem_readsOwnershipOnce() {
		Item item = register("상세");
		Item peer = register("같이 입은 옷");
		wear(item, 2);
		tagTogether(item, peer, 1);

		statistics.clear();
		itemQueryService.getItem(owner.getId(), item.getId());

		// 아이템 1 · 소유 구간 1 · 인증 횟수 1 · 미리보기 1 · 최근 착용일 1 · 판매 등록 1
		// · 자주 같이 입은 옷 2(집계 + 이름 조회)
		// 소유자 판정에 findCurrent 를 따로 치면 9 가 된다
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(8);
	}

	/** 두 아이템을 같은 게시물에 함께 태그한다 — 추천 블록 셋이 모두 채워지는 조건이다. */
	private void tagTogether(Item first, Item second, int count) {
		for (int i = 0; i < count; i++) {
			Post post = postRepository.save(
					Post.publish(owner, LocalDate.of(2026, 7, 1).plusDays(i), AspectRatio.RATIO_3_4, false));
			posts.add(post);

			PostImage image = postImageRepository.save(
					PostImage.of(post, "post/2026/07/%d.jpg".formatted(i), 0, true));
			tags.add(postItemTagRepository.save(
					PostItemTag.of(image, first, new BigDecimal("0.4"), new BigDecimal("0.6"))));
			tags.add(postItemTagRepository.save(
					PostItemTag.of(image, second, new BigDecimal("0.5"), new BigDecimal("0.7"))));
		}
	}

	private Item register(String name) {
		Long id = itemService.create(owner.getId(), ItemCreateRequest.builder()
				.name(name).brandName("Levis").categoryId(CATEGORY_ID)
				.sizeLabel("L").wearTarget(WearTarget.MALE)
				.coverImageKey("item/2026/09/a.jpg")
				.firstOwnedDate(LocalDate.of(2025, 2, 8))
				.build()).getId();

		Item item = itemRepository.findById(id).orElseThrow();
		items.add(item);
		return item;
	}

	/** 인증 기록을 쌓는다. 게시물과 대표 사진이 있어야 미리보기 쿼리가 돈다. */
	private void wear(Item item, int count) {
		ItemOwnership ownership = itemOwnershipRepository.findCurrent(item.getId()).orElseThrow();

		for (int i = 0; i < count; i++) {
			Post post = postRepository.save(
					Post.publish(owner, LocalDate.of(2026, 6, 1).plusDays(i), AspectRatio.RATIO_3_4, false));
			posts.add(post);
			postImageRepository.save(PostImage.of(post, "post/2026/06/%d.jpg".formatted(i), 0, true));
			itemWearRecordRepository.save(ItemWearRecord.certify(item, ownership, post, post.getWornDate()));
		}
	}
}
