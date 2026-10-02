package com.tenure.domain.category.service;

import com.tenure.domain.category.dto.response.CategoryResponse;
import com.tenure.domain.category.dto.response.SizeOptionResponse;
import com.tenure.domain.category.entity.Category;
import com.tenure.domain.category.entity.SizeOption;
import com.tenure.domain.category.enums.MeasurementField;
import com.tenure.domain.category.enums.MeasurementScheme;
import com.tenure.domain.category.enums.SizeScheme;
import com.tenure.domain.category.mapper.CategoryMapper;
import com.tenure.domain.category.repository.CategoryRepository;
import com.tenure.domain.category.repository.SizeOptionRepository;
import com.tenure.global.logging.Loggable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리 트리 조회.
 *
 * 대분류 10 · 세부 102 · 사이즈 칩 82 로 전부 고정 데이터다. 페이징도 필터도 없이 통째로 읽어
 * 메모리에서 트리를 만든다 — 두 번의 전체 조회가 재귀 쿼리보다 싸고 읽기 쉽다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryQueryService {

	private final CategoryRepository categoryRepository;
	private final SizeOptionRepository sizeOptionRepository;
	private final CategoryMapper categoryMapper;

	/**
	 * 카테고리 트리. 사이즈 칩은 대분류에만 싣는다 — 세부 카테고리는 부모의 칩을 그대로 쓴다.
	 */
	@Loggable("카테고리 트리 조회")
	public List<CategoryResponse> getTree() {
		Map<SizeScheme, List<SizeOptionResponse>> optionsByScheme = loadSizeOptions();

		List<Category> all = categoryRepository.findAll();
		Map<Long, List<Category>> childrenByParent = new HashMap<>();
		List<Category> roots = new ArrayList<>();

		for (Category category : all) {
			if (category.getParent() == null) {
				roots.add(category);
			} else {
				childrenByParent
						.computeIfAbsent(category.getParent().getId(), key -> new ArrayList<>())
						.add(category);
			}
		}

		roots.sort(Comparator.comparing(Category::getSortOrder));

		List<CategoryResponse> tree = new ArrayList<>();
		for (Category root : roots) {
			List<Category> children = childrenByParent.getOrDefault(root.getId(), List.of());
			children.sort(Comparator.comparing(Category::getSortOrder));

			List<CategoryResponse> childResponses = children.stream()
					.map(child -> categoryMapper.toCategoryResponse(
							child, List.of(), List.of(), List.of()))
					.toList();

			tree.add(categoryMapper.toCategoryResponse(
					root,
					optionsByScheme.getOrDefault(root.getSizeScheme(), List.of()),
					measurementFieldsOf(root),
					childResponses
			));
		}

		return tree;
	}

	/** 프론트가 이 목록대로 실측 입력란을 그린다. 세트가 없거나 NONE 이면 빈 목록이다. */
	private List<MeasurementField> measurementFieldsOf(Category root) {
		MeasurementScheme scheme = root.getMeasurementScheme();
		return scheme == null ? List.of() : List.copyOf(scheme.getFields());
	}

	private Map<SizeScheme, List<SizeOptionResponse>> loadSizeOptions() {
		Map<SizeScheme, List<SizeOptionResponse>> grouped = new EnumMap<>(SizeScheme.class);

		for (SizeOption option : sizeOptionRepository.findAllOrdered()) {
			grouped.computeIfAbsent(option.getScheme(), key -> new ArrayList<>())
					.add(categoryMapper.toSizeOptionResponse(option));
		}

		return grouped;
	}
}
