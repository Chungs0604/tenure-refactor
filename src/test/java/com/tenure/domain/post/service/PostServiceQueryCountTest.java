package com.tenure.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.item.dto.request.ItemCreateRequest;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.item.repository.ItemWearRecordRepository;
import com.tenure.domain.item.service.ItemService;
import com.tenure.domain.post.dto.request.PostCreateRequest;
import com.tenure.domain.post.dto.request.PostCreateRequest.PostImageRequest;
import com.tenure.domain.post.dto.request.PostCreateRequest.PostTagRequest;
import com.tenure.domain.post.dto.response.PostCreateResponse;
import com.tenure.domain.post.entity.Post;
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
 * OOTD 게시의 이미지×태그 이중 루프 쿼리 수를 센다.
 *
 * 태그마다 아이템 존재·소유 구간을 따로 조회하면(N+1) 태그가 늘어날수록 조회 쿼리도 같이 는다.
 * 늘어난 태그 수만큼 INSERT(태그·착용 기록)가 늘어나는 건 불가피하지만, 조회 쿼리는 요청 한 번에
 * 한 번만 나가야 한다 — 그래서 늘어난 양이 '태그당 INSERT 2건' 을 넘지 않는지로 판정한다.
 */
@DisplayName("PostService 쿼리 수")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PostServiceQueryCountTest extends IntegrationTestSupport {

	/** V2 시드의 고정 id — 긴소매 티셔츠 */
	private static final Long CATEGORY_ID = 116L;

	@Autowired private PostService postService;
	@Autowired private ItemService itemService;
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

	@BeforeEach
	void setUp() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		owner = userRepository.save(User.signUp(
				"post-count-%s@tenure.local".formatted(tag), "$2a$10$hashed", "홍길동",
				"821012345678", "post-count-%s".formatted(tag), null, Gender.MALE, 170, 64));

		statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
	}

	@AfterEach
	void tearDown() {
		postItemTagRepository.deleteAll(postItemTagRepository.findAll().stream()
				.filter(tag -> items.stream().anyMatch(item -> item.getId().equals(tag.getItem().getId())))
				.toList());
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
	@DisplayName("태그 개수가 늘어도 아이템 조회 쿼리는 태그마다 다시 나가지 않는다")
	void create_manyTaggedItems_itemLookupQueriesDoNotScale() {
		List<Item> twoItems = registerItems(2);

		statistics.clear();
		PostCreateResponse twoTagsResponse = postService.create(owner.getId(), createRequestFor(twoItems));
		long queriesForTwoTags = statistics.getPrepareStatementCount();
		posts.add(postRepository.findById(twoTagsResponse.getId()).orElseThrow());

		List<Item> eightItems = registerItems(8);

		statistics.clear();
		PostCreateResponse eightTagsResponse = postService.create(owner.getId(), createRequestFor(eightItems));
		long queriesForEightTags = statistics.getPrepareStatementCount();
		posts.add(postRepository.findById(eightTagsResponse.getId()).orElseThrow());

		long extraTags = eightItems.size() - twoItems.size();

		// 태그·착용 기록 INSERT 는 늘어난 태그 수만큼 불가피하게 는다(태그당 2건).
		// 태그마다 아이템·소유 구간을 따로 조회하면 거기에 태그당 조회 2건이 더 붙어
		// 늘어난 양이 태그당 4건으로 뛴다 — 그 차이로 N+1 복귀를 잡는다
		assertThat(queriesForEightTags - queriesForTwoTags).isLessThanOrEqualTo(extraTags * 2 + 2);
	}

	private List<Item> registerItems(int count) {
		List<Item> registered = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			registered.add(register("태그" + items.size()));
		}
		return registered;
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

	/** 사진당 태그 상한(4개, MAX_TAGS_PER_IMAGE=5 이내)을 넘지 않게 여러 사진으로 나눈다. */
	private PostCreateRequest createRequestFor(List<Item> taggedItems) {
		int perImage = 4;
		List<PostImageRequest> images = new ArrayList<>();

		for (int start = 0; start < taggedItems.size(); start += perImage) {
			int end = Math.min(start + perImage, taggedItems.size());
			List<PostTagRequest> tags = taggedItems.subList(start, end).stream()
					.map(item -> PostTagRequest.builder()
							.itemId(item.getId())
							.posX(new BigDecimal("0.4200"))
							.posY(new BigDecimal("0.6100"))
							.build())
					.toList();

			images.add(PostImageRequest.builder()
					.imageKey("post/2026/09/%d.jpg".formatted(start))
					.sortOrder(start)
					.isCover(start == 0)
					.tags(tags)
					.build());
		}

		return PostCreateRequest.builder()
				.wornDate(LocalDate.now())
				.aspectRatio(AspectRatio.RATIO_3_4)
				.images(images)
				.build();
	}
}
