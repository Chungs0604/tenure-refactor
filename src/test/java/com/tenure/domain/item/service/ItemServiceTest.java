package com.tenure.domain.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.tenure.domain.item.mapper.ItemMapper;
import com.tenure.domain.item.mapper.ItemMapperImpl;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.item.enums.SaleStatus;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import com.tenure.domain.item.repository.ItemMeasurementRepository;
import com.tenure.domain.item.repository.ItemConditionRepository;
import com.tenure.domain.category.enums.MeasurementField;
import com.tenure.domain.category.enums.MeasurementScheme;
import com.tenure.domain.item.entity.ItemMeasurement;
import com.tenure.domain.item.enums.ConditionCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.tenure.domain.item.dto.request.ItemForm;
import com.tenure.domain.item.entity.ItemCondition;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ItemService")
class ItemServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long OTHER_USER_ID = 2L;
	private static final Long ITEM_ID = 100L;
	private static final Long CATEGORY_ID = 116L;
	private static final Long ROOT_CATEGORY_ID = 2L;
	private static final LocalDate FIRST_OWNED = LocalDate.of(2025, 2, 8);

	@Mock private ItemRepository itemRepository;
	@Mock private ItemFormApplier itemFormApplier;
	@Mock private ItemOwnershipRepository itemOwnershipRepository;
	@Mock private TradeOrderRepository tradeOrderRepository;
	@Mock private UserRepository userRepository;

	// 매핑은 검증 대상이라 mock 이 아니라 실제 생성 구현을 넣는다
	@Spy private ItemMapper itemMapper = new ItemMapperImpl();

	@InjectMocks private ItemService itemService;

	@Captor private ArgumentCaptor<ItemOwnership> ownershipCaptor;

	@Nested
	@DisplayName("아이템 등록")
	class Create {

		@Test
		@DisplayName("아이템과 최초 소유 구간을 함께 만든다 — 구간이 없으면 게시물에 태그할 수 없다")
		void create_opensInitialOwnership() {
			givenUserAndCategory();

			itemService.create(USER_ID, request(LocalDate.of(2026, 5, 26), "L"));

			verify(itemOwnershipRepository).save(ownershipCaptor.capture());
			assertThat(ownershipCaptor.getValue().getStartDate()).isEqualTo(LocalDate.of(2026, 5, 26));
			assertThat(ownershipCaptor.getValue().getEndDate()).isNull();
		}

		@Test
		@DisplayName("최초 보유 날짜를 비우면 소유 구간은 등록일부터 열고 아이템의 날짜는 비워 둔다")
		void create_noFirstOwnedDate_opensFromToday() {
			givenUserAndCategory();

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

			ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);

			itemService.create(USER_ID, request(null, null));

			verify(itemRepository).save(itemCaptor.capture());
			assertThat(itemCaptor.getValue().getOfferAllowed()).isFalse();
			assertThat(itemCaptor.getValue().getWishCount()).isZero();
		}
		@Test
		@DisplayName("카테고리·사이즈 검증은 ItemFormApplier 에 맡긴다")
		void create_delegatesValidationToApplier() {
			givenUserAndCategory();

			itemService.create(USER_ID, request(null, "L"));

			verify(itemFormApplier).resolveCategory(CATEGORY_ID, "L");
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

			assertThatThrownBy(() -> itemService.update(USER_ID, ITEM_ID, updateRequest("LVC 1955 501", "L")))
					.isInstanceOf(CustomException.class)
					.extracting(ItemServiceTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_403);
		}

		@Test
		@DisplayName("폼 전체를 ItemFormApplier 에 넘긴다 — 반영 규칙은 거기가 정본이다")
		void update_delegatesWholeFormToApplier() {
			Item item = item();
			givenUpdatableItem(item);

			itemService.update(USER_ID, ITEM_ID, updateRequest("Third Cut", "L"));

			ArgumentCaptor<ItemForm> captor = ArgumentCaptor.forClass(ItemForm.class);
			verify(itemFormApplier).apply(eq(item), captor.capture());

			assertThat(captor.getValue().getName()).isEqualTo("Third Cut");
			assertThat(captor.getValue().getBrandName()).isEqualTo("Levis");
			assertThat(captor.getValue().getSizeLabel()).isEqualTo("L");
			assertThat(captor.getValue().getWearTarget()).isEqualTo(WearTarget.MALE);
		}

		@Test
		@DisplayName("최초 보유 날짜는 등록 뒤에 바뀌지 않는다 — 구매자가 보고 산 기록의 첫 줄이다")
		void update_firstOwnedDate_immutable() {
			Item item = item();
			ItemOwnership ownership = ownershipOf(item, user());
			LocalDate registered = item.getFirstOwnedDate();

			given(itemRepository.findActiveWithCategory(ITEM_ID)).willReturn(Optional.of(item));
			given(itemOwnershipRepository.findCurrent(ITEM_ID)).willReturn(Optional.of(ownership));
			given(itemFormApplier.apply(any(), any())).willReturn(leafCategory());

			itemService.update(USER_ID, ITEM_ID, updateRequest("이름만 고침", "L"));

			assertThat(item.getFirstOwnedDate()).isEqualTo(registered);
			assertThat(ownership.getStartDate()).isEqualTo(FIRST_OWNED);
		}

		@Test
		@DisplayName("대표 사진은 여기서 바꾸지 않는다 — 시트에 입력란이 없다")
		void update_doesNotTouchCoverImage() {
			Item item = item();
			givenUpdatableItem(item);

			itemService.update(USER_ID, ITEM_ID, updateRequest("Third Cut", "L"));

			assertThat(item.getCoverImageKey()).isEqualTo("item/cover.jpg");
		}

		private void givenUpdatableItem(Item item) {
			given(itemRepository.findActiveWithCategory(ITEM_ID)).willReturn(Optional.of(item));
			given(itemOwnershipRepository.findCurrent(ITEM_ID))
					.willReturn(Optional.of(ownershipOf(item, user())));
			given(itemFormApplier.apply(any(), any())).willReturn(leafCategory());
		}
	}

	@Nested
	@DisplayName("구매 제안 허용 토글")
	class ChangeOfferAllowed {

		@Test
		@DisplayName("상세 수정과 따로 켠다 — 선택 모달의 항목이라 폼을 함께 보내지 않는다")
		void changeOfferAllowed_togglesAlone() {
			Item item = item();
			given(itemRepository.findActiveWithCategory(ITEM_ID)).willReturn(Optional.of(item));
			given(itemOwnershipRepository.findCurrent(ITEM_ID))
					.willReturn(Optional.of(ownershipOf(item, user())));

			itemService.changeOfferAllowed(USER_ID, ITEM_ID, true);

			assertThat(item.getOfferAllowed()).isTrue();
			assertThat(item.getName()).isEqualTo("LVC 1955 501");
		}

		@Test
		@DisplayName("현재 소유자가 아니면 막는다")
		void changeOfferAllowed_notOwner_forbidden() {
			Item item = item();
			given(itemRepository.findActiveWithCategory(ITEM_ID)).willReturn(Optional.of(item));
			given(itemOwnershipRepository.findCurrent(ITEM_ID))
					.willReturn(Optional.of(ownershipOf(item, user(OTHER_USER_ID))));

			assertThatThrownBy(() -> itemService.changeOfferAllowed(USER_ID, ITEM_ID, true))
					.isInstanceOf(CustomException.class)
					.extracting(ItemServiceTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_403);
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
			item.changeSaleStatus(SaleStatus.ON_SALE);

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
			given(tradeOrderRepository.existsInProgress(anyLong(), any())).willReturn(false);
		}
	}

	private void givenUserAndCategory() {
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
		given(itemFormApplier.resolveCategory(any(), any())).willReturn(leafCategory());
		given(itemFormApplier.apply(any(), any())).willReturn(leafCategory());
		given(itemRepository.save(any(Item.class))).willAnswer(invocation -> {
			Item saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", ITEM_ID);
			return saved;
		});
	}


	private static ItemForm form(String name, String sizeLabel) {
		return ItemForm.builder()
				.categoryId(CATEGORY_ID)
				.name(name)
				.brandName("Levis")
				.sizeLabel(sizeLabel)
				.wearTarget(WearTarget.MALE)
				.build();
	}

	private static Category leafCategory(MeasurementScheme scheme) {
		Category leaf = leafCategory();
		ReflectionTestUtils.setField(leaf.getParent(), "measurementScheme", scheme);
		return leaf;
	}

	/** 상세 수정 시트는 전체 폼이라 항상 전 필드를 보낸다. */
	private static ItemUpdateRequest updateRequest(String name, String sizeLabel) {
		return ItemUpdateRequest.builder()
				.name(name)
				.brandName("Levis")
				.categoryId(CATEGORY_ID)
				.sizeLabel(sizeLabel)
				.wearTarget(WearTarget.MALE)
				.build();
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
		ReflectionTestUtils.setField(root, "measurementScheme", MeasurementScheme.TOP);

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

	/** 등록 시점에 최초 보유 날짜를 넣어 둔 아이템. 그 값이 최초 구간의 시작일과 같다. */
	private static Item item() {
		Item item = Item.register(user(), leafCategory(), "LVC 1955 501", "Levis", "L",
				WearTarget.MALE, "item/cover.jpg", FIRST_OWNED);
		ReflectionTestUtils.setField(item, "id", ITEM_ID);
		return item;
	}

	private static ItemOwnership ownershipOf(Item item, User owner) {
		ItemOwnership ownership = ItemOwnership.openInitial(item, owner, FIRST_OWNED);
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
