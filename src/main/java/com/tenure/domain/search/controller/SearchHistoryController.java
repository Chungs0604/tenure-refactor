package com.tenure.domain.search.controller;

import com.tenure.domain.search.dto.response.SearchHistoryResponse;
import com.tenure.domain.search.service.SearchHistoryService;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchHistoryController implements SearchHistoryApi {

	private final SearchHistoryService searchHistoryService;

	@Override
	@GetMapping("/histories")
	public ResponseEntity<BaseResponse<SearchHistoryResponse>> getHistories(
			@AuthenticationPrincipal AuthUser authUser
	) {
		return ResponseEntity.ok(BaseResponse.success(searchHistoryService.getHistories(authUser.id())));
	}

	@Override
	@DeleteMapping("/histories/{historyId}")
	public ResponseEntity<BaseResponse<Void>> deleteKeyword(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long historyId
	) {
		searchHistoryService.deleteKeyword(authUser.id(), historyId);
		return ResponseEntity.ok(BaseResponse.success());
	}

	@Override
	@DeleteMapping("/histories")
	public ResponseEntity<BaseResponse<Void>> deleteAllKeywords(
			@AuthenticationPrincipal AuthUser authUser
	) {
		searchHistoryService.deleteAllKeywords(authUser.id());
		return ResponseEntity.ok(BaseResponse.success());
	}

	@Override
	@DeleteMapping("/recent-views/users/{targetId}")
	public ResponseEntity<BaseResponse<Void>> removeRecentUser(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long targetId
	) {
		searchHistoryService.removeRecentUser(authUser.id(), targetId);
		return ResponseEntity.ok(BaseResponse.success());
	}

	@Override
	@DeleteMapping("/recent-views/users")
	public ResponseEntity<BaseResponse<Void>> clearRecentUsers(
			@AuthenticationPrincipal AuthUser authUser
	) {
		searchHistoryService.clearRecentUsers(authUser.id());
		return ResponseEntity.ok(BaseResponse.success());
	}
}
