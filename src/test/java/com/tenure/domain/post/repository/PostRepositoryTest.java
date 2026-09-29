package com.tenure.domain.post.repository;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.entity.ItemWearRecord;
import com.tenure.domain.item.enums.WearRecordVoidReason;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemWearRecordRepository;
import com.tenure.domain.listing.enums.SaleStatus;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.listing.repository.ListingFeaturedPostRepository;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.entity.PostImage;
import com.tenure.domain.post.entity.PostItemTag;
import com.tenure.domain.post.entity.PostLike;
import com.tenure.domain.post.entity.PostSave;
import com.tenure.domain.post.enums.AspectRatio;
import com.tenure.domain.post.enums.FeedTab;
import com.tenure.domain.post.enums.MyPostTab;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.wishlist.repository.WishlistRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게시물 조회 쿼리.
 *
 * H2 가 아니라 실제 MySQL 에서 돈다. 차단 제외의 서브쿼리와 월 범위 조건은 방언에 따라
 * 결과가 갈릴 수 있어 여기서 확인해 둔다. QueryDSL 빈은 RepositorySliceTestSupport 가 켠다.
 */
@DisplayName("게시물 조회 쿼리")
class PostRepositoryTest extends RepositorySliceTestSupport {

	/** 커서 조회는 size + 1 건을 읽는다. 테스트 데이터가 적어 넉넉히 준다. */
	private static final int SIZE = 20;

	@Autowired private EntityManager em;
	@Autowired private PostRepository postRepository;
	@Autowired private PostImageRepository postImageRepository;
	@Autowired private PostLikeRepository postLikeRepository;
	@Autowired private PostSaveRepository postSaveRepository;
	@Autowired private ItemOwnershipRepository itemOwnershipRepository;
	@Autowired private ItemWearRecordRepository itemWearRecordRepository;
	@Autowired private ItemListingRepository itemListingRepository;
	@Autowired private ListingFeaturedPostRepository listingFeaturedPostRepository;
	@Autowired private WishlistRepository wishlistRepository;

	private User me;
	private User other;

	@BeforeEach
	void setUp() {
		me = persistUser("me@tenure.app", "Sujun");
		other = persistUser("other@tenure.app", "YuJin");
	}

	@Nested
	@DisplayName("홈 피드")
	class Feed {

		@Test
		@DisplayName("모두 탭은 최신 게시일 순이고 삭제된 게시물은 빠진다")
		void findFeed_allTab_ordersByCreatedAtDesc() {
			Post first = persistPost(other, LocalDate.of(2026, 6, 1));
			Post second = persistPost(other, LocalDate.of(2026, 6, 2));
			Post deleted = persistPost(other, LocalDate.of(2026, 6, 3));
			deleted.softDelete();
			flush();

			List<Post> feed = postRepository.findFeed(me.getId(), FeedTab.ALL, null, SIZE);

			assertThat(feed).extracting(Post::getId)
					.containsExactly(second.getId(), first.getId());
		}

		@Test
		@DisplayName("내가 차단한 사용자의 게시물은 보이지 않는다")
		void findFeed_blockedByMe_excludesAuthor() {
			persistPost(other, LocalDate.of(2026, 6, 1));
			block(me, other);

			assertThat(postRepository.findFeed(me.getId(), FeedTab.ALL, null, SIZE)).isEmpty();
		}

		@Test
		@DisplayName("나를 차단한 사용자의 게시물도 보이지 않는다 — 한쪽만 막으면 절반만 지켜진다")
		void findFeed_blockedMe_excludesAuthor() {
			persistPost(other, LocalDate.of(2026, 6, 1));
			block(other, me);

			assertThat(postRepository.findFeed(me.getId(), FeedTab.ALL, null, SIZE)).isEmpty();
		}

		@Test
		@DisplayName("팔로우 탭은 내가 팔로우한 사용자의 게시물만 담는다")
		void findFeed_followingTab_onlyFollowedAuthors() {
			User stranger = persistUser("stranger@tenure.app", "Stranger");
			Post followed = persistPost(other, LocalDate.of(2026, 6, 1));
			persistPost(stranger, LocalDate.of(2026, 6, 2));
			follow(me, other);

			List<Post> feed = postRepository.findFeed(me.getId(), FeedTab.FOLLOWING, null, SIZE);

			assertThat(feed).extracting(Post::getId).containsExactly(followed.getId());
		}

		@Test
		@DisplayName("모두 탭에는 본인 게시물도 담긴다")
		void findFeed_allTab_includesOwnPost() {
			Post mine = persistPost(me, LocalDate.of(2026, 6, 1));

			assertThat(postRepository.findFeed(me.getId(), FeedTab.ALL, null, SIZE))
					.extracting(Post::getId).containsExactly(mine.getId());
		}

		@Test
		@DisplayName("커서를 주면 그 뒤부터 가져온다")
		void findFeed_withCursor_returnsRowsAfterIt() {
			Post first = persistPost(other, LocalDate.of(2026, 6, 1));
			Post second = persistPost(other, LocalDate.of(2026, 6, 2));
			flush();
			em.clear();

			// 첫 페이지의 마지막 행(= 최신인 second)으로 커서를 만든다
			Post lastOfFirstPage = postRepository.findFeed(me.getId(), FeedTab.ALL, null, 1).get(0);
			Cursor cursor = Cursor.of(lastOfFirstPage.getCreatedAt(), lastOfFirstPage.getId());

			List<Post> next = postRepository.findFeed(me.getId(), FeedTab.ALL, cursor, SIZE);

			assertThat(lastOfFirstPage.getId()).isEqualTo(second.getId());
			assertThat(next).extracting(Post::getId).containsExactly(first.getId());
		}

		@Test
		@DisplayName("게시일이 같아도 id 로 갈라 건너뛰지 않는다 — 한 초에 여러 건이 올라온다")
		void findFeed_sameCreatedAt_tieBreaksById() {
			Post first = persistPost(other, LocalDate.of(2026, 6, 1));
			Post second = persistPost(other, LocalDate.of(2026, 6, 2));
			flush();
			// 두 건의 게시일을 같은 값으로 맞춘다
			LocalDateTime sameMoment = LocalDateTime.now().withNano(0);
			backdateCreatedAt(first, sameMoment);
			backdateCreatedAt(second, sameMoment);

			List<Post> firstPage = postRepository.findFeed(me.getId(), FeedTab.ALL, null, 1);
			Post head = firstPage.get(0);
			List<Post> next = postRepository.findFeed(me.getId(), FeedTab.ALL,
					Cursor.of(head.getCreatedAt(), head.getId()), SIZE);

			// id 조건이 없으면 둘 다 빠지거나 둘 다 다시 나온다
			assertThat(head.getId()).isEqualTo(second.getId());
			assertThat(next).extracting(Post::getId).containsExactly(first.getId());
		}

		@Test
		@DisplayName("size + 1 건까지 읽는다 — 다음 페이지 유무를 세지 않고 안다")
		void findFeed_readsOneMoreThanSize() {
			persistPost(other, LocalDate.of(2026, 6, 1));
			persistPost(other, LocalDate.of(2026, 6, 2));
			persistPost(other, LocalDate.of(2026, 6, 3));

			assertThat(postRepository.findFeed(me.getId(), FeedTab.ALL, null, 2)).hasSize(3);
		}
	}

	@Nested
	@DisplayName("마이페이지 · 타인 프로필")
	class Lists {

		@Test
		@DisplayName("게시물 탭은 착용일 최신순이다")
		void findMyPosts_postsTab_ordersByWornDateDesc() {
			Post older = persistPost(me, LocalDate.of(2026, 5, 20));
			Post newer = persistPost(me, LocalDate.of(2026, 6, 21));
			persistPost(other, LocalDate.of(2026, 6, 30));

			List<Post> posts = postRepository.findMyPosts(
					me.getId(), MyPostTab.POSTS, null, null, SIZE);

			assertThat(posts).extracting(Post::getId)
					.containsExactly(newer.getId(), older.getId());
		}

		@Test
		@DisplayName("월을 고르면 그 달의 착용 기록만 남는다")
		void findMyPosts_withYearMonth_filtersByMonth() {
			persistPost(me, LocalDate.of(2026, 5, 31));
			Post june = persistPost(me, LocalDate.of(2026, 6, 1));

			List<Post> posts = postRepository.findMyPosts(
					me.getId(), MyPostTab.POSTS, java.time.YearMonth.of(2026, 6), null, SIZE);

			assertThat(posts).extracting(Post::getId).containsExactly(june.getId());
		}

		@Test
		@DisplayName("좋아요 탭은 내가 누른 남의 게시물을 담는다")
		void findMyPosts_likedTab_returnsLikedPosts() {
			Post liked = persistPost(other, LocalDate.of(2026, 6, 1));
			persistPost(other, LocalDate.of(2026, 6, 2));
			em.persist(PostLike.of(liked, me));
			flush();

			List<Post> posts = postRepository.findMyPosts(
					me.getId(), MyPostTab.LIKED, null, null, SIZE);

			assertThat(posts).extracting(Post::getId).containsExactly(liked.getId());
		}

		@Test
		@DisplayName("저장 탭은 내가 저장한 게시물을 담는다")
		void findMyPosts_savedTab_returnsSavedPosts() {
			Post saved = persistPost(other, LocalDate.of(2026, 6, 1));
			em.persist(PostSave.of(saved, me));
			flush();

			List<Post> posts = postRepository.findMyPosts(
					me.getId(), MyPostTab.SAVED, null, null, SIZE);

			assertThat(posts).extracting(Post::getId).containsExactly(saved.getId());
		}

		@Test
		@DisplayName("착용일이 같아도 id 로 갈라 건너뛰지 않는다 — 하루에 여러 벌을 올린다")
		void findMyPosts_sameWornDate_tieBreaksById() {
			LocalDate sameDay = LocalDate.of(2026, 6, 21);
			Post first = persistPost(me, sameDay);
			Post second = persistPost(me, sameDay);

			List<Post> firstPage = postRepository.findMyPosts(
					me.getId(), MyPostTab.POSTS, null, null, 1);
			Post head = firstPage.get(0);
			List<Post> next = postRepository.findMyPosts(me.getId(), MyPostTab.POSTS, null,
					Cursor.of(head.getWornDate(), head.getId()), SIZE);

			assertThat(head.getId()).isEqualTo(second.getId());
			assertThat(next).extracting(Post::getId).containsExactly(first.getId());
		}

		@Test
		@DisplayName("타인 게시물은 그 사람 것만, 삭제된 것은 빼고 담는다")
		void findUserPosts_targetAuthor_excludesDeleted() {
			Post visible = persistPost(other, LocalDate.of(2026, 6, 1));
			Post deleted = persistPost(other, LocalDate.of(2026, 6, 2));
			deleted.softDelete();
			persistPost(me, LocalDate.of(2026, 6, 3));
			flush();

			List<Post> posts = postRepository.findUserPosts(other.getId(), null, SIZE);

			assertThat(posts).extracting(Post::getId).containsExactly(visible.getId());
		}
	}

	@Nested
	@DisplayName("레코드 승격 판정")
	class CountValidOotd {

		@Test
		@DisplayName("아이템 태그가 있는 게시물만 센다")
		void countValidOotd_onlyTaggedPosts() {
			Item item = persistItem(me);
			Post tagged = persistPost(me, LocalDate.of(2026, 6, 1));
			persistTag(tagged, item);
			persistPost(me, LocalDate.of(2026, 6, 2));
			flush();

			int count = postRepository.countValidOotd(me.getId(), LocalDateTime.now().minusDays(30));

			assertThat(count).isEqualTo(1);
		}

		@Test
		@DisplayName("삭제된 게시물은 세지 않는다")
		void countValidOotd_deletedPost_excluded() {
			Item item = persistItem(me);
			Post deleted = persistPost(me, LocalDate.of(2026, 6, 1));
			persistTag(deleted, item);
			deleted.softDelete();
			flush();

			assertThat(postRepository.countValidOotd(me.getId(), LocalDateTime.now().minusDays(30)))
					.isZero();
		}

		@Test
		@DisplayName("구간을 벗어난 게시물은 세지 않는다 — 기준은 착용일이 아니라 게시일이다")
		void countValidOotd_outsideWindow_excluded() {
			Item item = persistItem(me);
			Post old = persistPost(me, LocalDate.of(2026, 6, 1));
			persistTag(old, item);
			flush();
			backdateCreatedAt(old, LocalDateTime.now().minusDays(31));

			assertThat(postRepository.countValidOotd(me.getId(), LocalDateTime.now().minusDays(30)))
					.isZero();
		}
	}

	@Nested
	@DisplayName("응답 조립용 일괄 조회")
	class BatchQueries {

		@Test
		@DisplayName("대표 사진만 골라 온다")
		void findCovers_returnsOnlyCover() {
			Post post = persistPost(me, LocalDate.of(2026, 6, 1));
			em.persist(PostImage.of(post, "post/2026/06/cover.jpg", 0, true));
			em.persist(PostImage.of(post, "post/2026/06/second.jpg", 1, false));
			flush();

			List<PostImage> covers = postImageRepository.findCovers(List.of(post.getId()));

			assertThat(covers).hasSize(1);
			assertThat(covers.get(0).getImageKey()).isEqualTo("post/2026/06/cover.jpg");
		}

		@Test
		@DisplayName("좋아요·저장한 게시물 id 만 돌려준다")
		void findLikedAndSavedPostIds_returnsMatchingIds() {
			Post liked = persistPost(other, LocalDate.of(2026, 6, 1));
			Post saved = persistPost(other, LocalDate.of(2026, 6, 2));
			em.persist(PostLike.of(liked, me));
			em.persist(PostSave.of(saved, me));
			flush();

			List<Long> postIds = List.of(liked.getId(), saved.getId());

			assertThat(postLikeRepository.findLikedPostIds(me.getId(), postIds))
					.containsExactly(liked.getId());
			assertThat(postSaveRepository.findSavedPostIds(me.getId(), postIds))
					.containsExactly(saved.getId());
		}

		@Test
		@DisplayName("아이템별 최신 판매 등록을 하나씩 골라 온다 — 재판매마다 행이 쌓인다")
		void findLatestByItemIds_returnsLatestPerItem() {
			Item item = persistItem(me);
			insertListing(item, me, SaleStatus.SOLD, 300000);
			long latestId = insertListing(item, me, SaleStatus.ON_SALE, 360000);

			assertThat(itemListingRepository.findLatestByItemIds(List.of(item.getId())))
					.extracting(listing -> listing.getId())
					.containsExactly(latestId);
		}

		@Test
		@DisplayName("판매 중인 상품의 대표 OOTD 면 true, 판매가 끝난 등록이면 false 다")
		void existsByPostIdAndSaleStatus_dependsOnSaleStatus() {
			Item item = persistItem(me);
			Post post = persistPost(me, LocalDate.of(2026, 6, 1));
			flush();

			long onSaleId = insertListing(item, me, SaleStatus.ON_SALE, 360000);
			insertFeaturedPost(onSaleId, post.getId());

			assertThat(listingFeaturedPostRepository
					.existsByPostIdAndSaleStatus(post.getId(), SaleStatus.ON_SALE)).isTrue();
			assertThat(listingFeaturedPostRepository
					.existsByPostIdAndSaleStatus(post.getId(), SaleStatus.SOLD)).isFalse();
		}

		@Test
		@DisplayName("알림을 켠 아이템 id 만 돌려준다 — 담아만 두고 알림을 끈 것은 빠진다")
		void findAlarmEnabledItemIds_returnsAlarmOnOnly() {
			Item alarmOn = persistItem(me);
			Item alarmOff = persistItem(me);
			Item plain = persistItem(me);
			flush();
			insertWishlist(me.getId(), alarmOn.getId(), true);
			insertWishlist(me.getId(), alarmOff.getId(), false);

			assertThat(wishlistRepository.findAlarmEnabledItemIds(
					me.getId(), List.of(alarmOn.getId(), alarmOff.getId(), plain.getId())))
					.containsExactly(alarmOn.getId());
		}
	}

	@Nested
	@DisplayName("소유 구간과 착용 기록")
	class OwnershipAndRecords {

		@Test
		@DisplayName("열린 구간만 현재 구간이다")
		void findCurrent_openInterval_returnsIt() {
			Item item = persistItem(me);
			ItemOwnership ownership = persistOwnership(item, me);

			assertThat(itemOwnershipRepository.findCurrent(item.getId()))
					.get().extracting(ItemOwnership::getId).isEqualTo(ownership.getId());
		}

		@Test
		@DisplayName("구간이 닫히면 현재 구간이 없다 — 남에게 넘어간 아이템이다")
		void findCurrent_closedInterval_returnsEmpty() {
			Item item = persistItem(me);
			ItemOwnership ownership = persistOwnership(item, me);
			ownership.close(LocalDate.of(2026, 7, 10));
			flush();

			assertThat(itemOwnershipRepository.findCurrent(item.getId())).isEmpty();
		}

		@Test
		@DisplayName("게시물이 적립한 인증을 소유 구간과 함께 가져온다")
		void findAllByPostId_returnsRecordsWithOwnership() {
			Item item = persistItem(me);
			ItemOwnership ownership = persistOwnership(item, me);
			Post post = persistPost(me, LocalDate.of(2026, 6, 1));
			em.persist(ItemWearRecord.certify(item, ownership, post, post.getWornDate()));
			flush();
			em.clear();

			List<ItemWearRecord> records = itemWearRecordRepository.findAllByPostId(post.getId());

			assertThat(records).hasSize(1);
			// fetch join 이라 프록시 초기화 없이 읽힌다
			assertThat(records.get(0).getOwnership().isClosed()).isFalse();
		}

		@Test
		@DisplayName("무효화된 인증도 (게시물, 아이템) 으로 찾힌다 — 되살려야 유니크 제약을 피한다")
		void findByPostIdAndItemId_voidedRecord_isFound() {
			Item item = persistItem(me);
			ItemOwnership ownership = persistOwnership(item, me);
			Post post = persistPost(me, LocalDate.of(2026, 6, 1));
			ItemWearRecord record = ItemWearRecord.certify(item, ownership, post, post.getWornDate());
			record.invalidate(WearRecordVoidReason.TAG_CHANGED);
			em.persist(record);
			flush();

			assertThat(itemWearRecordRepository
					.findByPostIdAndItemId(post.getId(), item.getId()))
					.get().extracting(ItemWearRecord::isVoided).isEqualTo(true);
		}
	}

	private User persistUser(String email, String nickname) {
		User user = User.signUp(email, "$2a$10$hashed", "홍길동",
				"821012345678", nickname, null, Gender.MALE, 170, 64);
		em.persist(user);
		flush();
		return user;
	}

	private Post persistPost(User author, LocalDate wornDate) {
		Post post = Post.publish(author, wornDate, AspectRatio.RATIO_3_4, false);
		em.persist(post);
		flush();
		return post;
	}

	private Item persistItem(User owner) {
		// 세부 카테고리는 V2 시드가 넣어 둔 값을 쓴다 (대분류 1~10, 세부 101~202)
		Category category = em.find(Category.class, 101L);
		Item item = Item.register(owner, category, "LVC 1955 501", "Levis", "L",
				WearTarget.UNISEX, "item/2026/09/a.jpg", null, LocalDate.of(2025, 2, 8));
		em.persist(item);
		flush();
		return item;
	}

	private ItemOwnership persistOwnership(Item item, User owner) {
		ItemOwnership ownership = ItemOwnership.openInitial(item, owner, LocalDate.of(2025, 2, 8));
		em.persist(ownership);
		flush();
		return ownership;
	}

	private void persistTag(Post post, Item item) {
		PostImage image = PostImage.of(post, "post/2026/06/a.jpg", 0, true);
		em.persist(image);
		em.persist(PostItemTag.of(image, item, new BigDecimal("0.4200"), new BigDecimal("0.6100")));
		flush();
	}

	private void follow(User follower, User following) {
		em.createNativeQuery("insert into follow (follower_id, following_id, created_at) "
						+ "values (?, ?, now(6))")
				.setParameter(1, follower.getId())
				.setParameter(2, following.getId())
				.executeUpdate();
	}

	private void block(User blocker, User blocked) {
		em.createNativeQuery("insert into user_block (blocker_id, blocked_id, created_at) "
						+ "values (?, ?, now(6))")
				.setParameter(1, blocker.getId())
				.setParameter(2, blocked.getId())
				.executeUpdate();
	}

	/**
	 * 판매 등록을 직접 넣는다 — listing 도메인에 아직 팩토리가 없다. 여기서 확인하는 것은
	 * '아이템별 최신 한 건' 을 고르는 쿼리라 행의 출처는 중요하지 않다.
	 */
	private long insertListing(Item item, User seller, SaleStatus saleStatus, int price) {
		em.createNativeQuery("insert into item_listing "
						+ "(item_id, seller_id, sale_status, price, shipping_fee, "
						+ "fee_bearer, created_at, updated_at) "
						+ "values (?, ?, ?, ?, 0, 'SELLER', now(6), now(6))")
				.setParameter(1, item.getId())
				.setParameter(2, seller.getId())
				.setParameter(3, saleStatus.name())
				.setParameter(4, price)
				.executeUpdate();

		return ((Number) em.createNativeQuery("select max(id) from item_listing").getSingleResult())
				.longValue();
	}

	private void insertFeaturedPost(long listingId, long postId) {
		em.createNativeQuery("insert into listing_featured_post (listing_id, post_id, sort_order) "
						+ "values (?, ?, 0)")
				.setParameter(1, listingId)
				.setParameter(2, postId)
				.executeUpdate();
	}

	private void insertWishlist(long userId, long itemId, boolean alarmEnabled) {
		em.createNativeQuery("insert into wishlist (user_id, item_id, alarm_enabled, created_at) "
						+ "values (?, ?, ?, now(6))")
				.setParameter(1, userId)
				.setParameter(2, itemId)
				.setParameter(3, alarmEnabled)
				.executeUpdate();
	}

	/** 게시일은 감사 컬럼이라 엔티티로는 과거로 돌릴 수 없다. */
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
