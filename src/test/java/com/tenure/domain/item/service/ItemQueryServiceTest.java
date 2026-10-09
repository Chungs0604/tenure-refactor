package com.tenure.domain.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.item.dto.response.ItemOwnershipHistoryResponse;
import com.tenure.domain.item.dto.response.ItemResponse;
import com.tenure.domain.item.dto.response.ItemSummaryResponse;
import com.tenure.domain.item.dto.response.TaggableItemResponse;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.mapper.ItemMapper;
import com.tenure.domain.item.mapper.ItemMapperImpl;
import com.tenure.domain.item.repository.ItemCurrentWearCountRow;
import com.tenure.domain.item.repository.ItemLastWornDateRow;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.item.repository.ItemWearRecordRepository;
import com.tenure.domain.item.repository.OwnershipPreviewRow;
import com.tenure.domain.item.repository.OwnershipWearCountRow;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.post.mapper.PostMapper;
import com.tenure.domain.post.repository.PostImageRepository;
import com.tenure.domain.post.repository.PostItemTagRepository;
import com.tenure.domain.post.repository.PostLikeRepository;
import com.tenure.domain.post.repository.PostSaveRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.storage.FileStorage;
import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ItemQueryService")
class ItemQueryServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long OTHER_USER_ID = 2L;
	private static final Long ITEM_ID = 100L;
	private static final Long LISTING_ID = 300L;

	@Mock private ItemRepository itemRepository;
	@Mock private ItemOwnershipRepository itemOwnershipRepository;
	@Mock private ItemWearRecordRepository itemWearRecordRepository;
	@Mock private ItemListingRepository itemListingRepository;
	@Mock private PostItemTagRepository postItemTagRepository;
	@Mock private PostImageRepository postImageRepository;
	@Mock private PostLikeRepository postLikeRepository;
	@Mock private PostSaveRepository postSaveRepository;
	@Mock private PostMapper postMapper;

	// 매핑은 검증 대상이라 mock 이 아니라 실제 생성 구현을 넣는다
	@Spy private ItemMapper itemMapper = new ItemMapperImpl();
	@Mock private FileStorage fileStorage;

	@InjectMocks private ItemQueryService itemQueryService;

	@Nested
	@DisplayName("내 아이템 목록")
	class GetMyItems {

		@Test
		@DisplayName("판매 등록이 없으면 아이템의 미판매 상태가 그대로 내려간다")
		void neverListed_notForSale() {
			givenUrlPrefix();
			given(itemRepository.findOwnedItems(anyLong(), any(), any(), anyInt()))
					.willReturn(List.of(item()));
			given(itemWearRecordRepository.findLastWornDates(any())).willReturn(List.of());
			given(itemListingRepository.findOpenByItemIds(any())).willReturn(List.of());

			CursorSlice<ItemSummaryResponse> slice =
					itemQueryService.getMyItems(USER_ID, null, null, 20);

			ItemSummaryResponse row = slice.getContent().get(0);
			assertThat(row.getSaleStatus()).isEqualTo(SaleStatus.NOT_FOR_SALE);
			assertThat(row.getListing()).isNull();
		}

		@Test
		@DisplayName("열린 판매 등록이 있으면 아이템 상태와 그 가격을 함께 싣는다")
		void listed_carriesStatusAndPrice() {
			givenUrlPrefix();
			Item item = item();
			item.changeSaleStatus(SaleStatus.ON_SALE);

			// mock 안에서 mock 을 만들면 스터빙이 겹쳐 Mockito 가 미완성으로 본다
			ItemListing listing = listing(item);

			given(itemRepository.findOwnedItems(anyLong(), any(), any(), anyInt()))
					.willReturn(List.of(item));
			given(itemWearRecordRepository.findLastWornDates(any()))
					.willReturn(List.of(new ItemLastWornDateRow(ITEM_ID, LocalDate.of(2026, 5, 26))));
			given(itemListingRepository.findOpenByItemIds(any())).willReturn(List.of(listing));

			ItemSummaryResponse row = itemQueryService.getMyItems(USER_ID, null, null, 20)
					.getContent().get(0);

			assertThat(row.getSaleStatus()).isEqualTo(SaleStatus.ON_SALE);
			assertThat(row.getListing().getId()).isEqualTo(LISTING_ID);
			assertThat(row.getListing().getPrice()).isEqualTo("360000");
			assertThat(row.getLastWornDate()).isEqualTo(LocalDate.of(2026, 5, 26));
		}

		@Test
		@DisplayName("화면이 그리는 것만 싣는다 — 판매 등록이 없으면 listing 만 빈다")
		void list_carriesOnlyWhatScreenDraws() {
			givenUrlPrefix();
			given(itemRepository.findOwnedItems(anyLong(), any(), any(), anyInt()))
					.willReturn(List.of(item()));
			given(itemWearRecordRepository.findLastWornDates(any()))
					.willReturn(List.of(new ItemLastWornDateRow(ITEM_ID, LocalDate.of(2026, 5, 26))));
			given(itemListingRepository.findOpenByItemIds(any())).willReturn(List.of());

			ItemSummaryResponse row = itemQueryService.getMyItems(USER_ID, null, null, 20)
					.getContent().get(0);

			// 관심 수·구매 제안은 목록 화면이 그리지 않아 DTO 에 칸 자체가 없다
			assertThat(row).hasNoNullFieldsOrPropertiesExcept("listing");
			assertThat(row.getSaleStatus()).isEqualTo(SaleStatus.NOT_FOR_SALE);
			assertThat(row.getLastWornDate()).isEqualTo(LocalDate.of(2026, 5, 26));
		}
	}

	@Nested
	@DisplayName("태그 가능한 내 아이템")
	class GetTaggableItems {

		@Test
		@DisplayName("최근 착용일과 현재 구간의 인증 횟수를 함께 준다 — 행이 '최근 착용 7일 전 · OOTD 인증 : 2회' 다")
		void taggable_carriesLastWornDateAndWearCount() {
			givenUrlPrefix();
			given(itemRepository.findOwnedItems(anyLong(), any(), any(), anyInt()))
					.willReturn(List.of(item()));
			given(itemWearRecordRepository.findLastWornDates(any()))
					.willReturn(List.of(new ItemLastWornDateRow(ITEM_ID, LocalDate.of(2026, 5, 26))));
			given(itemWearRecordRepository.countCurrentOwnershipWears(any()))
					.willReturn(List.of(new ItemCurrentWearCountRow(ITEM_ID, 2L)));

			TaggableItemResponse row = itemQueryService.getTaggableItems(USER_ID, null, null, 20)
					.getContent().get(0);

			assertThat(row.getLastWornDate()).isEqualTo(LocalDate.of(2026, 5, 26));
			assertThat(row.getWearCount()).isEqualTo(2);
		}

		@Test
		@DisplayName("인증이 없으면 0 회다 — 방금 등록한 아이템도 태그할 수 있다")
		void taggable_noWearRecord_zero() {
			givenUrlPrefix();
			given(itemRepository.findOwnedItems(anyLong(), any(), any(), anyInt()))
					.willReturn(List.of(item()));
			given(itemWearRecordRepository.findLastWornDates(any())).willReturn(List.of());
			given(itemWearRecordRepository.countCurrentOwnershipWears(any())).willReturn(List.of());

			TaggableItemResponse row = itemQueryService.getTaggableItems(USER_ID, null, null, 20)
					.getContent().get(0);

			assertThat(row.getWearCount()).isZero();
			assertThat(row.getLastWornDate()).isNull();
		}

		@Test
		@DisplayName("판매 상태는 그리지 않으므로 판매 등록을 읽지 않는다")
		void taggable_doesNotTouchListing() {
			givenUrlPrefix();
			given(itemRepository.findOwnedItems(anyLong(), any(), any(), anyInt()))
					.willReturn(List.of(item()));
			given(itemWearRecordRepository.findLastWornDates(any())).willReturn(List.of());
			given(itemWearRecordRepository.countCurrentOwnershipWears(any())).willReturn(List.of());

			TaggableItemResponse row = itemQueryService.getTaggableItems(USER_ID, null, null, 20)
					.getContent().get(0);

			// 판매 상태를 담을 칸 자체가 DTO 에 없다
			assertThat(row).hasNoNullFieldsOrPropertiesExcept("lastWornDate");
			verifyNoInteractions(itemListingRepository);
		}
	}

	@Nested
	@DisplayName("아이템 상세")
	class GetItem {

		@Test
		@DisplayName("현재 소유자가 아니면 막는다 — 이 화면에 수정·삭제 진입이 있다")
		void notOwner_forbidden() {
			Item item = item();
			given(itemRepository.findActiveWithCategory(ITEM_ID)).willReturn(Optional.of(item));
			given(itemOwnershipRepository.findCurrent(ITEM_ID))
					.willReturn(Optional.of(ownership(item, user(OTHER_USER_ID))));

			assertThatThrownBy(() -> itemQueryService.getItem(USER_ID, ITEM_ID))
					.isInstanceOf(CustomException.class)
					.extracting(ItemQueryServiceTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_403);
		}

		@Test
		@DisplayName("소유 구간 카드에 인증 횟수와 미리보기 사진을 채운다")
		void ownershipCard_hasWearCountAndPreviews() {
			givenUrlPrefix();
			Item item = item();
			ItemOwnership ownership = ownership(item, user(USER_ID));

			given(itemRepository.findActiveWithCategory(ITEM_ID)).willReturn(Optional.of(item));
			given(itemOwnershipRepository.findCurrent(ITEM_ID)).willReturn(Optional.of(ownership));
			given(itemOwnershipRepository.findAllByItemId(ITEM_ID)).willReturn(List.of(ownership));
			given(itemWearRecordRepository.countByOwnership(ITEM_ID))
					.willReturn(List.of(new OwnershipWearCountRow(ownership.getId(), 12L)));
			given(itemWearRecordRepository.findOwnershipPreviews(ITEM_ID)).willReturn(List.of(
					new OwnershipPreviewRow(ownership.getId(), "post/a.jpg"),
					new OwnershipPreviewRow(ownership.getId(), "post/b.jpg")));
			given(itemWearRecordRepository.findLastWornDates(any())).willReturn(List.of());
			given(itemListingRepository.findOpenByItemId(ITEM_ID)).willReturn(Optional.empty());
			given(postItemTagRepository.findFrequentlyWornWith(anyLong(), any())).willReturn(List.of());

			ItemResponse response = itemQueryService.getItem(USER_ID, ITEM_ID);

			assertThat(response.getOwnerships()).hasSize(1);
			assertThat(response.getOwnerships().get(0).getWearCount()).isEqualTo(12);
			assertThat(response.getOwnerships().get(0).getPreviewImages())
					.containsExactly("/uploads/post/a.jpg", "/uploads/post/b.jpg");
			assertThat(response.getTotalWearCount()).isEqualTo(12);
			assertThat(response.getSaleStatus()).isEqualTo(SaleStatus.NOT_FOR_SALE);
		}
	}

	@Nested
	@DisplayName("Tenure 기록 기준")
	class GetOwnerships {

		@Test
		@DisplayName("소유자가 아니어도 볼 수 있다 — 구매자 시점의 상품 상세가 같은 섹션을 쓴다")
		void notOwner_allowed() {
			givenUrlPrefix();
			Item item = item();
			ItemOwnership ownership = ownership(item, user(OTHER_USER_ID));

			given(itemRepository.findActive(ITEM_ID)).willReturn(Optional.of(item));
			given(itemOwnershipRepository.findAllByItemId(ITEM_ID)).willReturn(List.of(ownership));
			given(itemWearRecordRepository.countByOwnership(ITEM_ID)).willReturn(List.of());
			given(itemWearRecordRepository.findOwnershipPreviews(ITEM_ID)).willReturn(List.of());
			given(itemWearRecordRepository.findLastWornDates(any())).willReturn(List.of());

			ItemOwnershipHistoryResponse response = itemQueryService.getOwnerships(ITEM_ID);

			assertThat(response.getOwnerships()).hasSize(1);
			assertThat(response.getTotalWearCount()).isZero();
		}

		@Test
		@DisplayName("없는 아이템은 404 다")
		void unknownItem_notFound() {
			given(itemRepository.findActive(ITEM_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> itemQueryService.getOwnerships(ITEM_ID))
					.isInstanceOf(CustomException.class)
					.extracting(ItemQueryServiceTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_404);
		}
	}

	@Nested
	@DisplayName("아이템 히스토리")
	class GetHistory {

		@Test
		@DisplayName("없는 아이템은 404 다 — 빈 목록으로 돌려주면 구분되지 않는다")
		void unknownItem_notFound() {
			given(itemRepository.findActive(ITEM_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> itemQueryService.getHistory(ITEM_ID, null, 20))
					.isInstanceOf(CustomException.class)
					.extracting(ItemQueryServiceTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_404);
		}
	}

	private void givenUrlPrefix() {
		given(fileStorage.toUrl(anyString())).willAnswer(invocation -> "/uploads/" + invocation.getArgument(0));
	}

	private static Item item() {
		Item item = Item.register(user(USER_ID), leafCategory(), "LVC 1955 501", "Levis", "L",
				WearTarget.MALE, "item/cover.jpg", null);
		ReflectionTestUtils.setField(item, "id", ITEM_ID);
		ReflectionTestUtils.setField(item, "createdAt", java.time.LocalDateTime.now());
		return item;
	}

	private static ItemListing listing(Item item) {
		ItemListing listing = mock(ItemListing.class);
		given(listing.getId()).willReturn(LISTING_ID);
		given(listing.getItem()).willReturn(item);
		given(listing.getPrice()).willReturn(360000);
		return listing;
	}

	private static ItemOwnership ownership(Item item, User owner) {
		ItemOwnership ownership = ItemOwnership.openInitial(item, owner, LocalDate.of(2026, 7, 10));
		ReflectionTestUtils.setField(ownership, "id", 900L);
		return ownership;
	}

	private static Category leafCategory() {
		Category root = newCategory();
		ReflectionTestUtils.setField(root, "id", 2L);
		ReflectionTestUtils.setField(root, "name", "상의");
		ReflectionTestUtils.setField(root, "depth", 1);

		Category leaf = newCategory();
		ReflectionTestUtils.setField(leaf, "id", 116L);
		ReflectionTestUtils.setField(leaf, "name", "긴소매 티셔츠");
		ReflectionTestUtils.setField(leaf, "depth", 2);
		ReflectionTestUtils.setField(leaf, "parent", root);
		return leaf;
	}

	/** 카테고리는 seed 가 정본이라 팩토리가 없다 — 기본 생성자를 열어 만든다. */
	private static Category newCategory() {
		try {
			Constructor<Category> constructor = Category.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Category 기본 생성자를 열 수 없다", e);
		}
	}

	private static User user(Long id) {
		User user = User.signUp("a@b.com", "$2a$10$hashed", "홍길동", "821012345678",
				"유진나라", "profile/a.jpg", Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
