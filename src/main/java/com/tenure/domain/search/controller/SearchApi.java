package com.tenure.domain.search.controller;

import com.tenure.domain.search.dto.response.RecommendationResponse;
import com.tenure.domain.search.dto.response.SearchPostListResponse;
import com.tenure.domain.search.dto.response.SuggestedKeywordResponse;
import com.tenure.domain.search.enums.AuthorGenderFilter;
import com.tenure.domain.search.enums.PostSortType;
import com.tenure.domain.search.enums.RecommendationSection;
import com.tenure.domain.search.enums.SaleStatusFilter;
import com.tenure.domain.user.dto.response.UserSummaryResponse;
import com.tenure.global.pagination.CursorResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/** 검색(`/api/v1/search`) 조회·추천 API. 기록 관리(히스토리·최근 본 사용자 삭제)는 SearchHistoryApi 가 맡는다. */
@Validated
@Tag(name = "검색", description = "게시물·계정 검색, 추천 섹션 API")
public interface SearchApi {

	@Operation(summary = "게시물 검색", description = "필터·정렬 조합. 추천 순(기본)은 매칭 등급 → rank_score → id 순(DEC-023).")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	ResponseEntity<BaseResponse<SearchPostListResponse>> searchPosts(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "검색어") @Size(max = 100) String keyword,
			@Parameter(description = "판매 상태 3모드. 미지정 시 전체") SaleStatusFilter saleStatus,
			@Parameter(description = "게시자 성별. 미지정 시 전체") AuthorGenderFilter authorGender,
			@Parameter(description = "세부 카테고리 id 목록") List<Long> categoryIds,
			@Parameter(description = "키 최소(cm)") Integer heightMin,
			@Parameter(description = "키 최대(cm)") Integer heightMax,
			@Parameter(description = "몸무게 최소(kg)") Integer weightMin,
			@Parameter(description = "몸무게 최대(kg)") Integer weightMax,
			@Parameter(description = "정렬. 미지정 시 추천 순") PostSortType sort,
			@Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 비운다") String cursor,
			@Parameter(description = "페이지 크기") @Min(1) @Max(100) int size);

	@Operation(summary = "계정 검색", description = "닉네임 부분 일치.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	ResponseEntity<BaseResponse<CursorResponse<UserSummaryResponse>>> searchUsers(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "검색어", required = true) @Size(max = 100) String keyword,
			@Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 비운다") String cursor,
			@Parameter(description = "페이지 크기") @Min(1) @Max(100) int size);

	@Operation(summary = "검색 메인 추천 4블록", description = "유사·인기 스타일·최신·인기 사용자. 섹션마다 미리보기만 준다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	ResponseEntity<BaseResponse<RecommendationResponse>> getRecommendations(
			@Parameter(hidden = true) AuthUser authUser);

	@Operation(summary = "추천 검색어", description = "search_history 집계(DEC-020). 초기엔 빈 배열.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	ResponseEntity<BaseResponse<List<SuggestedKeywordResponse>>> getSuggestedKeywords(
			@Parameter(hidden = true) AuthUser authUser);

	@Operation(summary = "추천 섹션 더보기", description = "POPULAR_USER 만 응답이 사용자 목록이다.")
	@ApiResponse(responseCode = "400", description = "COMMON_400 — section 값이 잘못됨")
	ResponseEntity<BaseResponse<?>> getRecommendationSection(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "SIMILAR_TO_RECENT|POPULAR_STYLE|NEW_OOTD|POPULAR_USER") RecommendationSection section,
			@Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 비운다") String cursor,
			@Parameter(description = "페이지 크기") @Min(1) @Max(100) int size);
}
