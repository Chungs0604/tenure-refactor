package com.tenure.domain.category.mapper;

import com.tenure.domain.category.dto.response.CategoryResponse;
import com.tenure.domain.category.dto.response.SizeOptionResponse;
import com.tenure.domain.category.entity.Category;
import com.tenure.domain.category.entity.SizeOption;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** 엔티티 → 응답 단방향 변환. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CategoryMapper {

	@Mapping(target = "sizeOptions", source = "sizeOptions")
	@Mapping(target = "children", source = "children")
	CategoryResponse toCategoryResponse(
			Category category, List<SizeOptionResponse> sizeOptions, List<CategoryResponse> children
	);

	SizeOptionResponse toSizeOptionResponse(SizeOption sizeOption);
}
