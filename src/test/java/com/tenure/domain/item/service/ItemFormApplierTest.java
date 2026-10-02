package com.tenure.domain.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.category.enums.MeasurementField;
import com.tenure.domain.category.enums.MeasurementScheme;
import com.tenure.domain.category.enums.SizeScheme;
import com.tenure.domain.category.exception.CategoryErrorCode;
import com.tenure.domain.category.repository.CategoryRepository;
import com.tenure.domain.category.repository.SizeOptionRepository;
import com.tenure.domain.item.dto.request.ItemForm;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemCondition;
import com.tenure.domain.item.entity.ItemMeasurement;
import com.tenure.domain.item.enums.ConditionCode;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.repository.ItemConditionRepository;
import com.tenure.domain.item.repository.ItemMeasurementRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 아이템 폼 반영. 상세 수정 시트와 판매 전환 폼이 같은 경로를 탄다.
 *
 * 이 규칙이 두 서비스에 복제돼 있던 동안 null 처리가 서로 반대로 갈렸고, 카테고리를 바꿔도
 * 실측이 옛 세트로 남았다. 여기로 모은 뒤의 동작을 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("아이템 폼 반영")
class ItemFormApplierTest {

	private static final Long ITEM_ID = 100L;
	private static final Long CATEGORY_ID = 116L;
	private static final Long ROOT_CATEGORY_ID = 2L;

	@Mock private CategoryRepository categoryRepository;
	@Mock private SizeOptionRepository sizeOptionRepository;
	@Mock private ItemMeasurementRepository itemMeasurementRepository;
	@Mock private ItemConditionRepository itemConditionRepository;

	@InjectMocks private ItemFormApplier itemFormApplier;

	@Nested
	@DisplayName("카테고리와 사이즈")
	class CategoryAndSize {

		@Test
		@DisplayName("대분류 id 를 보내면 막는다 — 화면은 세부 카테고리까지 고르게 한다")
		void resolveCategory_rootCategory_rejected() {
			given(categoryRepository.findLeafWithParent(ROOT_CATEGORY_ID)).willReturn(Optional.empty());
			given(categoryRepository.existsById(ROOT_CATEGORY_ID)).willReturn(true);

			assertThatThrownBy(() -> itemFormApplier.resolveCategory(ROOT_CATEGORY_ID, "L"))
					.isInstanceOf(CustomException.class)
					.extracting(ItemFormApplierTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_400_CATEGORY);
		}

		@Test
		@DisplayName("없는 카테고리는 404 다")
		void resolveCategory_unknown_notFound() {
			given(categoryRepository.findLeafWithParent(CATEGORY_ID)).willReturn(Optional.empty());
			given(categoryRepository.existsById(CATEGORY_ID)).willReturn(false);

			assertThatThrownBy(() -> itemFormApplier.resolveCategory(CATEGORY_ID, "L"))
					.isInstanceOf(CustomException.class)
					.extracting(ItemFormApplierTest::errorCodeOf)
					.isEqualTo(CategoryErrorCode.CATEGORY_404);
		}

		@Test
		@DisplayName("폼 전체를 아이템에 교체한다 — 보낸 것만 바꾸는 부분 수정이 아니다")
		void apply_replacesWholeForm() {
			Item item = item();
			givenLeafCategory();
			given(sizeOptionRepository.existsBySchemeAndLabel(SizeScheme.TOP, "L")).willReturn(true);

			itemFormApplier.apply(item, ItemForm.builder()
					.categoryId(CATEGORY_ID).name("Third Cut").brandName("Levis")
					.sizeLabel("L").wearTarget(WearTarget.FEMALE).build());

			assertThat(item.getName()).isEqualTo("Third Cut");
			assertThat(item.getWearTarget()).isEqualTo(WearTarget.FEMALE);
		}

		@Test
		@DisplayName("사이즈를 비워 보내면 비운다 — 부분 전송이 아니라 교체다")
		void apply_blankSize_clears() {
			Item item = item();
			givenLeafCategory();

			itemFormApplier.apply(item, form(null));

			assertThat(item.getSizeLabel()).isNull();
		}

		@Test
		@DisplayName("칩에 없는 사이즈는 막는다 — 공개 페이지에 오타가 남으면 안 된다")
		void resolveCategory_sizeNotInScheme_rejected() {
			givenLeafCategory();
			given(sizeOptionRepository.existsBySchemeAndLabel(SizeScheme.TOP, "999XL")).willReturn(false);

			assertThatThrownBy(() -> itemFormApplier.resolveCategory(CATEGORY_ID, "999XL"))
					.isInstanceOf(CustomException.class)
					.extracting(ItemFormApplierTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_400_SIZE);
		}

		@Test
		@DisplayName("사이즈를 비우면 검증하지 않는다 — 등록 폼에 필수 표시가 없다")
		void resolveCategory_blankSize_skipsValidation() {
			givenLeafCategory();

			itemFormApplier.resolveCategory(CATEGORY_ID, null);

			verify(sizeOptionRepository, never()).existsBySchemeAndLabel(any(), anyString());
		}
	}

	@Nested
	@DisplayName("실측")
	class Measurements {

		@Test
		@DisplayName("세트 안의 항목을 일부만 채워도 통과한다 — 입력란에 필수 표시가 없다")
		void replaceMeasurements_partial_passes() {
			itemFormApplier.replaceMeasurements(item(), leafCategory(MeasurementScheme.TOP), Map.of(
					MeasurementField.SHOULDER_WIDTH, BigDecimal.valueOf(45.0),
					MeasurementField.CHEST_WIDTH, BigDecimal.valueOf(52.0)));

			ArgumentCaptor<ItemMeasurement> captor = ArgumentCaptor.forClass(ItemMeasurement.class);
			verify(itemMeasurementRepository).save(captor.capture());
			assertThat(captor.getValue().getShoulderWidth()).isEqualByComparingTo("45.0");
			assertThat(captor.getValue().getSleeveLength()).isNull();
		}

		@Test
		@DisplayName("세트 밖의 항목이 오면 막는다 — 상의에 허리 단면은 없다")
		void replaceMeasurements_outsideScheme_rejected() {
			assertThatThrownBy(() -> itemFormApplier.replaceMeasurements(
					item(), leafCategory(MeasurementScheme.TOP), Map.of(
							MeasurementField.SHOULDER_WIDTH, BigDecimal.valueOf(45.0),
							MeasurementField.WAIST_WIDTH, BigDecimal.valueOf(39.0))))
					.isInstanceOf(CustomException.class)
					.extracting(ItemFormApplierTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_400_MEASUREMENT);
		}

		@Test
		@DisplayName("한 칸도 안 채우면 행을 만들지 않는다")
		void replaceMeasurements_empty_doesNotSave() {
			itemFormApplier.replaceMeasurements(item(), leafCategory(MeasurementScheme.TOP), Map.of());

			verify(itemMeasurementRepository, never()).save(any());
		}

		@Test
		@DisplayName("값이 null 인 키는 안 보낸 것으로 본다")
		void replaceMeasurements_nullValueTreatedAsAbsent() {
			Map<MeasurementField, BigDecimal> values = new HashMap<>();
			values.put(MeasurementField.SHOULDER_WIDTH, BigDecimal.valueOf(45.0));
			values.put(MeasurementField.CHEST_WIDTH, null);

			itemFormApplier.replaceMeasurements(item(), leafCategory(MeasurementScheme.TOP), values);

			ArgumentCaptor<ItemMeasurement> captor = ArgumentCaptor.forClass(ItemMeasurement.class);
			verify(itemMeasurementRepository).save(captor.capture());
			assertThat(captor.getValue().getChestWidth()).isNull();
		}

		@Test
		@DisplayName("대분류를 바꾸면 맞지 않게 된 실측을 비운다 — 신발에 어깨 너비가 남으면 안 된다")
		void apply_categoryChanged_discardsMismatchedMeasurement() {
			Item item = item();
			ItemMeasurement stale = ItemMeasurement.of(item, Map.of(
					MeasurementField.SHOULDER_WIDTH, BigDecimal.valueOf(45.0)));

			given(itemMeasurementRepository.findByItemId(ITEM_ID)).willReturn(Optional.of(stale));
			given(categoryRepository.findLeafWithParent(CATEGORY_ID))
					.willReturn(Optional.of(leafCategory(MeasurementScheme.NONE)));

			itemFormApplier.apply(item, form(null));

			verify(itemMeasurementRepository).delete(stale);
		}

		@Test
		@DisplayName("대분류가 그대로면 실측을 건드리지 않는다")
		void apply_sameScheme_keepsMeasurement() {
			Item item = item();
			ItemMeasurement kept = ItemMeasurement.of(item, Map.of(
					MeasurementField.SHOULDER_WIDTH, BigDecimal.valueOf(45.0)));

			given(itemMeasurementRepository.findByItemId(ITEM_ID)).willReturn(Optional.of(kept));
			givenLeafCategory();
			given(sizeOptionRepository.existsBySchemeAndLabel(SizeScheme.TOP, "L")).willReturn(true);

			itemFormApplier.apply(item, form("L"));

			verify(itemMeasurementRepository, never()).delete(any());
		}
	}

	@Nested
	@DisplayName("상태 이상 체크")
	class Conditions {

		@Test
		@DisplayName("같은 코드를 두 번 보내도 행은 하나다")
		void replaceConditions_deduplicates() {
			itemFormApplier.replaceConditions(item(),
					List.of(ConditionCode.STAIN, ConditionCode.STAIN, ConditionCode.TEAR));

			ArgumentCaptor<List<ItemCondition>> captor = ArgumentCaptor.forClass(List.class);
			verify(itemConditionRepository).saveAll(captor.capture());
			assertThat(captor.getValue()).hasSize(2);
		}

		@Test
		@DisplayName("비워 보내면 전부 지우고 끝낸다")
		void replaceConditions_empty_onlyClears() {
			itemFormApplier.replaceConditions(item(), List.of());

			verify(itemConditionRepository).deleteAllByItemId(ITEM_ID);
			verify(itemConditionRepository, never()).saveAll(any());
		}
	}

	// ── 픽스처

	private void givenLeafCategory() {
		given(categoryRepository.findLeafWithParent(CATEGORY_ID))
				.willReturn(Optional.of(leafCategory(MeasurementScheme.TOP)));
	}

	private static ItemForm form(String sizeLabel) {
		return ItemForm.builder()
				.categoryId(CATEGORY_ID)
				.name("LVC 1955 501")
				.brandName("Levis")
				.sizeLabel(sizeLabel)
				.wearTarget(WearTarget.MALE)
				.build();
	}

	private static Category leafCategory(MeasurementScheme scheme) {
		Category root = newCategory();
		ReflectionTestUtils.setField(root, "id", ROOT_CATEGORY_ID);
		ReflectionTestUtils.setField(root, "depth", 1);
		ReflectionTestUtils.setField(root, "sizeScheme", SizeScheme.TOP);
		ReflectionTestUtils.setField(root, "measurementScheme", scheme);

		Category leaf = newCategory();
		ReflectionTestUtils.setField(leaf, "id", CATEGORY_ID);
		ReflectionTestUtils.setField(leaf, "depth", 2);
		ReflectionTestUtils.setField(leaf, "parent", root);
		return leaf;
	}

	private static Category newCategory() {
		try {
			var constructor = Category.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Item item() {
		Item item = Item.register(user(), leafCategory(MeasurementScheme.TOP), "LVC 1955 501",
				"Levis", "L", WearTarget.MALE, "item/cover.jpg", LocalDate.of(2026, 4, 20));
		ReflectionTestUtils.setField(item, "id", ITEM_ID);
		return item;
	}

	private static User user() {
		User user = User.signUp("a@b.com", "$2a$10$hashed", "홍길동", "821012345678",
				"Sujun", null, Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", 1L);
		return user;
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
