package com.tenure.domain.category.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.category.dto.response.CategoryResponse;
import com.tenure.domain.category.enums.MeasurementField;
import com.tenure.domain.category.enums.MeasurementScheme;
import com.tenure.domain.category.enums.SizeScheme;
import com.tenure.domain.category.enums.SizeSystem;
import com.tenure.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 카테고리 트리 조회.
 *
 * seed 를 그대로 읽으므로 실제 DB 로 돈다 — 값이 어긋나면 아이템 등록 화면이 칩을 그리지 못한다.
 */
@DisplayName("카테고리 트리 조회")
class CategoryQueryServiceTest extends IntegrationTestSupport {

	@Autowired private CategoryQueryService categoryQueryService;

	@Test
	@DisplayName("대분류 10종을 화면 순서대로 주고 세부 카테고리를 자식으로 단다")
	void getTree_returnsTenRootsInOrder() {
		List<CategoryResponse> tree = categoryQueryService.getTree();

		assertThat(tree).hasSize(10);
		assertThat(tree).extracting(CategoryResponse::getName)
				.containsExactly("아우터", "상의", "하의", "신발", "가방", "모자", "치마", "원피스", "액세서리", "쥬얼리");
		assertThat(tree).allSatisfy(root -> assertThat(root.getDepth()).isEqualTo(1));

		int leaves = tree.stream().mapToInt(root -> root.getChildren().size()).sum();
		assertThat(leaves).isEqualTo(102);
	}

	@Test
	@DisplayName("사이즈 칩은 대분류에만 싣는다 — 세부 카테고리는 부모의 칩을 그대로 쓴다")
	void getTree_sizeOptionsOnRootOnly() {
		CategoryResponse top = rootNamed("상의");

		assertThat(top.getSizeScheme()).isEqualTo(SizeScheme.TOP);
		assertThat(top.getSizeOptions()).hasSize(29);
		assertThat(top.getSizeOptions().get(0).getLabel()).isEqualTo("XS");
		assertThat(top.getSizeOptions().get(0).getSizeSystem()).isEqualTo(SizeSystem.ALPHA);

		assertThat(top.getChildren()).allSatisfy(leaf -> {
			assertThat(leaf.getSizeScheme()).isNull();
			assertThat(leaf.getSizeOptions()).isEmpty();
		});
	}

	@Test
	@DisplayName("실측 항목도 대분류에만 싣는다 — 프론트가 이 목록대로 입력란을 그린다")
	void getTree_measurementFieldsOnRootOnly() {
		CategoryResponse top = rootNamed("상의");

		assertThat(top.getMeasurementScheme()).isEqualTo(MeasurementScheme.TOP);
		assertThat(top.getMeasurementFields()).containsExactlyInAnyOrder(
				MeasurementField.SHOULDER_WIDTH, MeasurementField.CHEST_WIDTH,
				MeasurementField.SLEEVE_LENGTH, MeasurementField.TOTAL_LENGTH);

		assertThat(top.getChildren()).allSatisfy(leaf -> {
			assertThat(leaf.getMeasurementScheme()).isNull();
			assertThat(leaf.getMeasurementFields()).isEmpty();
		});
	}

	@Test
	@DisplayName("하의와 치마는 사이즈 칩이 같아도 실측 항목이 갈린다")
	void getTree_bottomAndSkirtDifferInMeasurements() {
		CategoryResponse bottom = rootNamed("하의");
		CategoryResponse skirt = rootNamed("치마");

		// 사이즈 칩 세트는 둘 다 BOTTOM 이다 — 그래서 실측을 따로 둬야 했다
		assertThat(bottom.getSizeScheme()).isEqualTo(skirt.getSizeScheme());

		assertThat(bottom.getMeasurementFields()).hasSize(6)
				.contains(MeasurementField.THIGH_WIDTH, MeasurementField.RISE, MeasurementField.INSEAM)
				.doesNotContain(MeasurementField.HIP_WIDTH);
		assertThat(skirt.getMeasurementFields()).hasSize(4)
				.contains(MeasurementField.HIP_WIDTH)
				.doesNotContain(MeasurementField.THIGH_WIDTH);
	}

	@Test
	@DisplayName("실측을 받지 않는 대분류는 항목이 비어 있다")
	void getTree_noneSchemeHasNoFields() {
		assertThat(rootNamed("신발").getMeasurementScheme()).isEqualTo(MeasurementScheme.NONE);
		assertThat(rootNamed("신발").getMeasurementFields()).isEmpty();
		assertThat(rootNamed("가방").getMeasurementFields()).isEmpty();
	}

	@Test
	@DisplayName("신발은 mm, 모자는 One size 다 — 카테고리마다 칩이 다르다")
	void getTree_schemeDiffersByCategory() {
		assertThat(rootNamed("신발").getSizeOptions())
				.hasSize(21)
				.allSatisfy(option -> assertThat(option.getSizeSystem()).isEqualTo(SizeSystem.MM));

		assertThat(rootNamed("모자").getSizeOptions())
				.singleElement()
				.satisfies(option -> assertThat(option.getLabel()).isEqualTo("One size"));
	}

	@Test
	@DisplayName("세부 카테고리 표기가 화면과 같다")
	void getTree_leafNamesMatchScreens() {
		assertThat(rootNamed("상의").getChildren()).extracting(CategoryResponse::getName)
				.contains("긴소매 티셔츠")
				.doesNotContain("긴팔 티셔츠");
	}

	private CategoryResponse rootNamed(String name) {
		return categoryQueryService.getTree().stream()
				.filter(root -> root.getName().equals(name))
				.findFirst()
				.orElseThrow();
	}
}
