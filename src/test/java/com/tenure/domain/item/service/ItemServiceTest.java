package com.tenure.domain.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.category.enums.SizeScheme;
import com.tenure.domain.category.exception.CategoryErrorCode;
import com.tenure.domain.category.repository.CategoryRepository;
import com.tenure.domain.category.repository.SizeOptionRepository;
import com.tenure.domain.item.dto.request.ItemCreateRequest;
import com.tenure.domain.item.dto.request.ItemUpdateRequest;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.order.repository.TradeOrderRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ItemService")
class ItemServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long OTHER_USER_ID = 2L;
	private static final Long ITEM_ID = 100L;
	private static final Long CATEGORY_ID = 116L;
	private static final Long ROOT_CATEGORY_ID = 2L;

	@Mock private ItemRepository itemRepository;
	@Mock private ItemOwnershipRepository itemOwnershipRepository;
	@Mock private CategoryRepository categoryRepository;
	@Mock private SizeOptionRepository sizeOptionRepository;
	@Mock private ItemListingRepository itemListingRepository;
	@Mock private TradeOrderRepository tradeOrderRepository;
	@Mock private UserRepository userRepository;

	@InjectMocks private ItemService itemService;

	@Captor private ArgumentCaptor<ItemOwnership> ownershipCaptor;

	@Nested
	@DisplayName("아이템 등록")
	class Create {

		@Test
		@DisplayName("아이템과 최초 소유 구간을 함께 만든다 — 구간이 없으면 게시물에 태그할 수 없다")
		void create_opensInitialOwnership() {
			givenUserAndCategory();
			givenSizeExists();

			itemService.create(USER_ID, request(LocalDate.of(2026, 5, 26), "L"));

			verify(itemOwnershipRepository).save(ownershipCaptor.capture());
			assertThat(ownershipCaptor.getValue().getStartDate()).isEqualTo(LocalDate.of(2026, 5, 26));
			assertThat(ownershipCaptor.getValue().getEndDate()).isNull();
		}

		@Test
		@DisplayName("최초 보유 날짜를 비우면 소유 구간은 등록일부터 열고 아이템의 날짜는 비워 둔다")
		void create_noFirstOwnedDate_opensFromToday() {
			givenUserAndCategory();
			givenSizeExists();

			ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);

			itemService.create(USER_ID, request(null, "L"));

			verify(itemRepository).save(itemCaptor.capture());
			verify(itemOwnershipRepository).save(ownershipCaptor.capture());

			assertThat(itemCaptor.getValue().getFirstOwnedDate()).isNull();
			assertThat(ownershipCaptor.getValue().getStartDate()).isEqualTo(LocalDate.now());
		}

		@Test
		@DisplayName("구매 제안 허용은 꺼진 채로 시작한다")
		void create_offerAllowedDefaultsFalse() {
			givenUserAndCategory();
			givenSizeExists();

			ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);

			itemService.create(USER_ID, request(null, null));

			verify(itemRepository).save(itemCaptor.capture());
			assertThat(itemCaptor.getValue().getOfferAllowed()).isFalse();
			assertThat(itemCaptor.getValue().getWishCount()).isZero();
		}

		@Test
		@DisplayName("카테고리에 없는 사이즈는 막는다 — 공개 페이지에 오타가 남으면 안 된다")
		void create_sizeNotInScheme_rejected() {
			givenUserAndCategory();
			given(sizeOptionRepository.existsBySchemeAndLabel(SizeScheme.TOP, "999XL")).willReturn(false);

			assertThatThrownBy(() -> itemService.create(USER_ID, request(null, "999XL")))
					.isInstanceOf(CustomException.class)
					.extracting(ItemServiceTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_400_SIZE);

			verify(itemRepository, never()).save(any());
		}

		@Test
		@DisplayName("사이즈를 비우면 검증하지 않는다 — 등록 폼에 필수 표시가 없다")
		void create_noSizeLabel_skipsValidation() {
			givenUserAndCategory();

			itemService.create(USER_ID, request(null, null));

			verify(sizeOptionRepository, never()).existsBySchemeAndLabel(any(), anyString());
		}

		@Test
		@DisplayName("대분류 id 를 보내면 막는다 — 화면은 세부 카테고리까지 고르게 한다")
		void create_rootCategory_rejected() {
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
			given(categoryRepository.findLeafWithParent(ROOT_CATEGORY_ID)).willReturn(Optional.empty());
			given(categoryRepository.existsById(ROOT_CATEGORY_ID)).willReturn(true);

			ItemCreateRequest request = ItemCreateRequest.builder()
					.name("LVC 1955 501").brandName("Levis").categoryId(ROOT_CATEGORY_ID)
					.wearTarget(WearTarget.MALE).coverImageKey("item/cover.jpg")
					.build();

			assertThatThrownBy(() -> itemService.create(USER_ID, request))
					.isInstanceOf(CustomException.class)
					.extracting(ItemServiceTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_400_CATEGORY);
		}

		@Test
		@DisplayName("없는 카테고리는 404 다")
		void create_unknownCategory_notFound() {
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
			given(categoryRepository.findLeafWithParent(CATEGORY_ID)).willReturn(Optional.empty());
			given(categoryRepository.existsById(CATEGORY_ID)).willReturn(false);

			assertThatThrownBy(() -> itemService.create(USER_ID, request(null, null)))
					.isInstanceOf(CustomException.class)
					.extracting(ItemServiceTest::errorCodeOf)
					.isEqualTo(CategoryErrorCode.CATEGORY_404);
		}
	}

	@Nested
	@DisplayName("아이템 수정")
	class Update {

		@Test
		@DisplayName("현재 소유자가 아니면 막는다 — 등록자여도 팔았으면 남의 옷이다")
		void update_notOwner_forbidden() {
			Item item = item();
			given(itemRepository.findActiveWithCategory(ITEM_ID)).willReturn(Optional.of(item));
			given(itemOwnershipRepository.findCurrent(ITEM_ID))
					.willReturn(Optional.of(ownershipOf(item, user(OTHER_USER_ID))));

			assertThatThrownBy(() -> itemService.update(USER_ID, ITEM_ID, ItemUpdateRequest.builder().build()))
					.isInstanceOf(CustomException.class)
					.extracting(ItemServiceTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_403);
		}

		@Test
		@DisplayName("최초 보유 날짜를 바꾸면 최초 소유 구간의 시작일도 함께 옮긴다")
		void update_firstOwnedDate_movesOwnershipStart() {
			Item item = item();
			ItemOwnership ownership = ownershipOf(item, user());

			given(itemRepository.findActiveWithCategory(ITEM_ID)).willReturn(Optional.of(item));
			given(itemOwnershipRepository.findCurrent(ITEM_ID)).willReturn(Optional.of(ownership));
			given(itemOwnershipRepository.countByItemId(ITEM_ID)).willReturn(1L);

			LocalDate moved = LocalDate.of(2025, 2, 8);
			itemService.update(USER_ID, ITEM_ID, ItemUpdateRequest.builder().firstOwnedDate(moved).build());

			assertThat(item.getFirstOwnedDate()).isEqualTo(moved);
			assertThat(ownership.getStartDate()).isEqualTo(moved);
		}

		@Test
		@DisplayName("거래 이력이 있으면 최초 보유 날짜가 잠긴다 — 구간 경계가 어긋난다")
		void update_traded_firstOwnedDateLocked() {
			Item item = item();
			given(itemRepository.findActiveWithCategory(ITEM_ID)).willReturn(Optional.of(item));
			given(itemOwnershipRepository.findCurrent(ITEM_ID)).willReturn(Optional.of(ownershipOf(item, user())));
			given(itemOwnershipRepository.countByItemId(ITEM_ID)).willReturn(2L);

			ItemUpdateRequest request = ItemUpdateRequest.builder()
					.firstOwnedDate(LocalDate.of(2025, 2, 8))
					.build();

			assertThatThrownBy(() -> itemService.update(USER_ID, ITEM_ID, request))
					.isInstanceOf(CustomException.class)
					.extracting(ItemServiceTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_409_TRADED);
		}

		@Test
		@DisplayName("구매 제안 허용을 여기서 켠다 — 미판매 아이템에는 판매 등록 행이 없다")
		void update_offerAllowed_toggled() {
			Item item = item();
			given(itemRepository.findActiveWithCategory(ITEM_ID)).willReturn(Optional.of(item));
			given(itemOwnershipRepository.findCurrent(ITEM_ID)).willReturn(Optional.of(ownershipOf(item, user())));

			itemService.update(USER_ID, ITEM_ID, ItemUpdateRequest.builder().offerAllowed(true).build());

			assertThat(item.getOfferAllowed()).isTrue();
		}
	}

	@Nested
	@DisplayName("아이템 삭제")
	class Delete {

		@Test
		@DisplayName("soft delete 한다 — 태그가 '삭제된 아이템' 으로 남아야 한다")
		void delete_softDeletes() {
			Item item = item();
			givenDeletableItem(item);

			itemService.delete(USER_ID, ITEM_ID);

			assertThat(item.isDeleted()).isTrue();
		}

		@Test
		@DisplayName("판매 중이면 막는다")
		void delete_onSale_rejected() {
			Item item = item();
			givenDeletableItem(item);
			given(itemListingRepository.existsOnSale(ITEM_ID)).willReturn(true);

			assertThatThrownBy(() -> itemService.delete(USER_ID, ITEM_ID))
					.isInstanceOf(CustomException.class)
					.extracting(ItemServiceTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_409_ON_SALE);

			assertThat(item.isDeleted()).isFalse();
		}

		@Test
		@DisplayName("끝나지 않은 거래가 있으면 막는다")
		void delete_orderInProgress_rejected() {
			Item item = item();
			givenDeletableItem(item);
			given(tradeOrderRepository.existsInProgress(anyLong(), any())).willReturn(true);

			assertThatThrownBy(() -> itemService.delete(USER_ID, ITEM_ID))
					.isInstanceOf(CustomException.class)
					.extracting(ItemServiceTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_409_ON_SALE);

			assertThat(item.isDeleted()).isFalse();
		}

		private void givenDeletableItem(Item item) {
			given(itemRepository.findActiveWithCategory(ITEM_ID)).willReturn(Optional.of(item));
			given(itemOwnershipRepository.findCurrent(ITEM_ID)).willReturn(Optional.of(ownershipOf(item, user())));
			given(itemListingRepository.existsOnSale(ITEM_ID)).willReturn(false);
			given(tradeOrderRepository.existsInProgress(anyLong(), any())).willReturn(false);
		}
	}

	private void givenUserAndCategory() {
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
		given(categoryRepository.findLeafWithParent(CATEGORY_ID)).willReturn(Optional.of(leafCategory()));
		given(itemRepository.save(any(Item.class))).willAnswer(invocation -> {
			Item saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", ITEM_ID);
			return saved;
		});
	}

	private void givenSizeExists() {
		given(sizeOptionRepository.existsBySchemeAndLabel(SizeScheme.TOP, "L")).willReturn(true);
	}

	private static ItemCreateRequest request(LocalDate firstOwnedDate, String sizeLabel) {
		return ItemCreateRequest.builder()
				.name("LVC 1955 501")
				.brandName("Levis")
				.categoryId(CATEGORY_ID)
				.sizeLabel(sizeLabel)
				.wearTarget(WearTarget.MALE)
				.coverImageKey("item/2026/09/a.jpg")
				.firstOwnedDate(firstOwnedDate)
				.build();
	}

	/** 사이즈 칩 세트는 세부 카테고리가 아니라 대분류가 갖는다. */
	private static Category leafCategory() {
		Category root = newCategory();
		ReflectionTestUtils.setField(root, "id", ROOT_CATEGORY_ID);
		ReflectionTestUtils.setField(root, "depth", 1);
		ReflectionTestUtils.setField(root, "sizeScheme", SizeScheme.TOP);

		Category leaf = newCategory();
		ReflectionTestUtils.setField(leaf, "id", CATEGORY_ID);
		ReflectionTestUtils.setField(leaf, "depth", 2);
		ReflectionTestUtils.setField(leaf, "parent", root);
		return leaf;
	}

	/**
	 * 카테고리는 seed 가 정본이라 애플리케이션이 만들지 않는다 — 팩토리가 없으므로
	 * 기본 생성자를 열어 만든다. 프로덕션에 테스트용 팩토리를 뚫지 않기 위해서다.
	 */
	private static Category newCategory() {
		try {
			Constructor<Category> constructor = Category.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Category 기본 생성자를 열 수 없다", e);
		}
	}

	private static Item item() {
		Item item = Item.register(user(), leafCategory(), "LVC 1955 501", "Levis", "L",
				WearTarget.MALE, "item/cover.jpg", null, null);
		ReflectionTestUtils.setField(item, "id", ITEM_ID);
		return item;
	}

	private static ItemOwnership ownershipOf(Item item, User owner) {
		ItemOwnership ownership = ItemOwnership.openInitial(item, owner, LocalDate.now());
		ReflectionTestUtils.setField(ownership, "id", 900L);
		return ownership;
	}

	private static User user() {
		return user(USER_ID);
	}

	private static User user(Long id) {
		User user = User.signUp("a@b.com", "$2a$10$hashed", "홍길동", "821012345678",
				"Sujun", null, Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
