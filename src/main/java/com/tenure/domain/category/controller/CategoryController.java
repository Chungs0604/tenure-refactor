package com.tenure.domain.category.controller;

import com.tenure.domain.category.dto.response.CategoryResponse;
import com.tenure.domain.category.service.CategoryQueryService;
import com.tenure.global.response.BaseResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController implements CategoryApi {

	private final CategoryQueryService categoryQueryService;

	// 카테고리 트리
	@Override
	@GetMapping
	public ResponseEntity<BaseResponse<List<CategoryResponse>>> getTree() {
		List<CategoryResponse> response = categoryQueryService.getTree();
		return ResponseEntity.ok(BaseResponse.success(response));
	}
}
