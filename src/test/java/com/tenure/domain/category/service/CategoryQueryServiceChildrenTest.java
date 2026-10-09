package com.tenure.domain.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.tenure.domain.category.dto.response.CategoryResponse;
import com.tenure.domain.category.entity.Category;
import com.tenure.domain.category.mapper.CategoryMapper;
import com.tenure.domain.category.mapper.CategoryMapperImpl;
import com.tenure.domain.category.repository.CategoryRepository;
import com.tenure.domain.category.repository.SizeOptionRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 자식이 없는 대분류에 대한 트리 조회 경계값.
 *
 * <p>seed 데이터는 대분류 10종이 전부 자식을 갖고 있어 {@link CategoryQueryServiceTest}(실제 DB)로는
 * 이 경로를 못 짚는다. 자식이 없는 대분류를 Mockito 로 직접 만들어 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("카테고리 트리 조회 — 자식 없는 대분류")
class CategoryQueryServiceChildrenTest {

	@Mock private CategoryRepository categoryRepository;
	@Mock private SizeOptionRepository sizeOptionRepository;

	// 매핑은 검증 대상이라 mock 이 아니라 실제 생성 구현을 넣는다
	@Spy private CategoryMapper categoryMapper = new CategoryMapperImpl();

	@InjectMocks private CategoryQueryService categoryQueryService;

	@Test
	@DisplayName("자식이 하나도 없는 대분류도 예외 없이 빈 children 으로 내려간다")
	void getTree_rootWithoutChildren_returnsEmptyChildren() {
		Category root = newCategory();
		ReflectionTestUtils.setField(root, "id", 1L);
		ReflectionTestUtils.setField(root, "name", "아우터");
		ReflectionTestUtils.setField(root, "depth", 1);
		ReflectionTestUtils.setField(root, "sortOrder", 1);

		given(categoryRepository.findAll()).willReturn(List.of(root));
		given(sizeOptionRepository.findAllOrdered()).willReturn(List.of());

		List<CategoryResponse> tree = categoryQueryService.getTree();

		assertThat(tree).singleElement().satisfies(response -> {
			assertThat(response.getName()).isEqualTo("아우터");
			assertThat(response.getChildren()).isEmpty();
		});
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
}
