package com.tenure.domain.category.controller;

import com.tenure.domain.category.dto.response.CategoryResponse;
import com.tenure.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "카테고리", description = "카테고리 트리 API")
public interface CategoryApi {

	@Operation(summary = "카테고리 트리",
			description = "대분류 10종과 세부 카테고리 102종. id 는 seed 고정값이라 환경마다 달라지지 않는다. "
					+ "사이즈 칩(sizeOptions)은 대분류에만 실린다 — 세부 카테고리는 부모의 칩을 그대로 쓴다. "
					+ "아이템 등록·수정은 여기서 받은 label 을 sizeLabel 로 그대로 보낸다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공")
	})
	ResponseEntity<BaseResponse<List<CategoryResponse>>> getTree();
}
