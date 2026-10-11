package com.tenure.domain.search.controller;

import com.tenure.domain.search.dto.response.SearchHistoryResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * 최근 검색어·최근 본 사용자의 조회·삭제. 저장소가 다르다 — 검색어는 RDB(개별 PK 로 삭제),
 * 최근 본 사용자는 Redis(사용자 id 로 바로 지운다, API-204/205 로는 지울 수 없다).
 */
@Tag(name = "검색 기록", description = "최근 검색어·최근 본 사용자 조회/삭제 API")
public interface SearchHistoryApi {

	@Operation(summary = "최근 검색어 + 최근 본 사용자 조회")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	ResponseEntity<BaseResponse<SearchHistoryResponse>> getHistories(
			@Parameter(hidden = true) AuthUser authUser);

	@Operation(summary = "최근 검색어 개별 삭제")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "삭제 성공"),
			@ApiResponse(responseCode = "403", description = "COMMON_403 — 본인의 검색 기록이 아님"),
			@ApiResponse(responseCode = "404", description = "SEARCH_404_HISTORY")
	})
	ResponseEntity<BaseResponse<Void>> deleteKeyword(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "search_history id") Long historyId);

	@Operation(summary = "최근 검색어 전체 삭제")
	@ApiResponse(responseCode = "200", description = "삭제 성공")
	ResponseEntity<BaseResponse<Void>> deleteAllKeywords(
			@Parameter(hidden = true) AuthUser authUser);

	@Operation(summary = "최근 본 사용자 개별 삭제", description = "이미 없는 대상을 지워도 멱등하게 성공한다.")
	@ApiResponse(responseCode = "200", description = "삭제 성공")
	ResponseEntity<BaseResponse<Void>> removeRecentUser(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "열람 대상 사용자 id") Long targetId);

	@Operation(summary = "최근 본 사용자 전체 삭제")
	@ApiResponse(responseCode = "200", description = "삭제 성공")
	ResponseEntity<BaseResponse<Void>> clearRecentUsers(
			@Parameter(hidden = true) AuthUser authUser);
}
