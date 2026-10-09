package com.tenure.domain.item.service;

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
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.repository.ItemConditionRepository;
import com.tenure.domain.item.repository.ItemMeasurementRepository;
import com.tenure.global.exception.CustomException;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 아이템 폼을 아이템에 반영한다. 상세 수정 시트와 판매 전환 폼이 같은 항목을 받는다.
 *
 * 서비스가 아니라 협력자다 — 트랜잭션을 열지 않고 부르는 쪽의 트랜잭션 안에서 돈다.
 * ItemService 와 ListingService 가 나란히 주입해 쓴다. 서비스끼리 부르게 두면 경계가 호출자에
 * 달려 흐려지고, 도메인이 늘수록 순환이 생길 자리가 된다.
 *
 * 여기 모은 이유는 사본이 둘이면 갈리기 때문이다. 실제로 사이즈 검증이 두 서비스에 복제돼
 * null 처리가 서로 반대였고, 카테고리를 바꿔도 실측이 옛 세트로 남아 있었다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemFormApplier {

	private final CategoryRepository categoryRepository;
	private final SizeOptionRepository sizeOptionRepository;
	private final ItemMeasurementRepository itemMeasurementRepository;
	private final ItemConditionRepository itemConditionRepository;

	/**
	 * 폼의 카테고리·사이즈를 검증하고 세부 카테고리를 돌려준다.
	 *
	 * 등록은 아이템이 아직 없어 반영할 대상이 없다 — 검증만 거치고 팩토리가 만든다.
	 */
	public Category resolveCategory(Long categoryId, String sizeLabel) {
		Category category = findLeafCategory(categoryId);
		validateSizeLabel(category, sizeLabel);
		return category;
	}

	/**
	 * 폼대로 반영하고 세부 카테고리를 돌려준다.
	 *
	 * 대분류가 바뀌면 실측 세트도 바뀌므로 맞지 않게 된 실측을 함께 비운다. 그러지 않으면
	 * 상의로 등록해 실측을 넣은 아이템을 신발로 옮겼을 때 어깨 너비가 그대로 남는다.
	 */
	public Category apply(Item item, ItemForm form) {
		// 카테고리만 바꿔도 검사가 필요함 — 상의(L)를 신발로 옮기면 L 은 신발 칩에 없음
		Category category = resolveCategory(form.getCategoryId(), form.getSizeLabel());

		item.update(
				category,
				form.getName(),
				form.getBrandName(),
				form.getSizeLabel(),
				form.getWearTarget()
		);

		discardMismatchedMeasurement(item, category);

		return category;
	}

	/**
	 * 실측 교체. 판매 전환 폼에만 입력란이 있어 거기서 들어온다.
	 *
	 * 보낸 항목이 대분류의 세트 안에 드는지만 본다 — 입력란에 필수 표시가 없어 일부만 채우거나
	 * 통째로 비울 수 있다. 막는 것은 세트 밖의 항목뿐이다.
	 */
	public void replaceMeasurements(
			Item item, Category category, Map<MeasurementField, BigDecimal> values
	) {
		MeasurementScheme scheme = category.resolveMeasurementScheme();
		Map<MeasurementField, BigDecimal> given = values == null ? Map.of() : values;
		Set<MeasurementField> sent = filledFields(given);

		// 세트 밖의 항목이 섞여 있으면 막음
		if (!scheme.getFields().containsAll(sent)) {
			Set<MeasurementField> unexpected = EnumSet.copyOf(sent);
			unexpected.removeAll(scheme.getFields());
			log.info("해당 대분류가 받지 않는 실측 항목이 왔다 categoryId={} scheme={} 항목={}",
					category.getId(), scheme, unexpected);
			throw new CustomException(ItemErrorCode.ITEM_400_MEASUREMENT);
		}

		// 아이템당 한 행이라 지우고 다시 넣음. 벌크 삭제로 지워야 DELETE 가 뒤의 save 보다
		// 먼저 DB 에 닿는다 — em.remove() 는 flush 때까지 미뤄져 INSERT 가 먼저 나간다.
		itemMeasurementRepository.deleteByItemId(item.getId());

		// 한 칸도 안 채웠으면 행을 만들지 않음
		if (sent.isEmpty()) {
			return;
		}

		itemMeasurementRepository.save(ItemMeasurement.of(item, given));
	}

	/** 상태 이상 체크 교체. 코드마다 행이 하나씩이라 개별 비교보다 비우는 편이 짧음 */
	public void replaceConditions(Item item, List<ConditionCode> conditions) {
		itemConditionRepository.deleteAllByItemId(item.getId());

		if (conditions == null || conditions.isEmpty()) {
			return;
		}

		// 같은 코드를 두 번 보내도 행은 하나임
		itemConditionRepository.saveAll(conditions.stream()
				.distinct()
				.map(code -> ItemCondition.of(item, code))
				.toList());
	}

	/** 대분류가 바뀌어 세트 밖이 된 실측은 남겨 둘 수 없다 — 상세가 엉뚱한 칸을 그린다. */
	private void discardMismatchedMeasurement(Item item, Category category) {
		Set<MeasurementField> accepted = category.resolveMeasurementScheme().getFields();

		itemMeasurementRepository.findByItemId(item.getId())
				.filter(measurement -> !accepted.containsAll(measurement.toValues().keySet()))
				.ifPresent(measurement -> {
					log.info("대분류가 바뀌어 맞지 않게 된 실측을 비운다 itemId={} categoryId={}",
							item.getId(), category.getId());
					itemMeasurementRepository.delete(measurement);
				});
	}

	private Category findLeafCategory(Long categoryId) {
		return categoryRepository.findLeafWithParent(categoryId)
				.orElseThrow(() -> {
					log.info("세부 카테고리가 아니거나 존재하지 않는다 categoryId={}", categoryId);
					return new CustomException(
							categoryRepository.existsById(categoryId)
									? ItemErrorCode.ITEM_400_CATEGORY
									: CategoryErrorCode.CATEGORY_404);
				});
	}

	/** 칩 세트는 세부 카테고리가 아니라 대분류가 가짐 */
	private void validateSizeLabel(Category leaf, String sizeLabel) {
		if (sizeLabel == null || sizeLabel.isBlank()) {
			return;
		}

		SizeScheme scheme = leaf.getParent().getSizeScheme();
		if (scheme == null || !sizeOptionRepository.existsBySchemeAndLabel(scheme, sizeLabel)) {
			log.info("카테고리에 없는 사이즈다 categoryId={} scheme={} sizeLabel={}",
					leaf.getId(), scheme, sizeLabel);
			throw new CustomException(ItemErrorCode.ITEM_400_SIZE);
		}
	}

	/** 값이 null 인 키는 안 보낸 것과 같게 봄 */
	private Set<MeasurementField> filledFields(Map<MeasurementField, BigDecimal> given) {
		return given.entrySet().stream()
				.filter(entry -> entry.getValue() != null)
				.map(Map.Entry::getKey)
				.collect(Collectors.toCollection(() -> EnumSet.noneOf(MeasurementField.class)));
	}
}
