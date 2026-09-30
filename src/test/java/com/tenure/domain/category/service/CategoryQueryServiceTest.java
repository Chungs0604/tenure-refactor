package com.tenure.domain.category.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.category.dto.response.CategoryResponse;
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
