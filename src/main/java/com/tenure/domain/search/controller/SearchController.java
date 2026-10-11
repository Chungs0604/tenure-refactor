package com.tenure.domain.search.controller;

import com.tenure.domain.search.dto.PostSearchCriteria;
import com.tenure.domain.search.dto.response.RecommendationResponse;
import com.tenure.domain.search.dto.response.SearchPostListResponse;
import com.tenure.domain.search.dto.response.SuggestedKeywordResponse;
import com.tenure.domain.search.enums.AuthorGenderFilter;
import com.tenure.domain.search.enums.PostSortType;
import com.tenure.domain.search.enums.RecommendationSection;
import com.tenure.domain.search.enums.SaleStatusFilter;
import com.tenure.domain.search.service.SearchService;
import com.tenure.domain.search.service.SearchService.PostSearchResult;
import com.tenure.domain.user.dto.response.UserSummaryResponse;
import com.tenure.global.pagination.CursorCodec;
import com.tenure.global.pagination.CursorResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController implements SearchApi {

	private final SearchService searchService;
	private final CursorCodec cursorCodec;

	@Override
	@GetMapping("/posts")
	public ResponseEntity<BaseResponse<SearchPostListResponse>> searchPosts(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "ALL") SaleStatusFilter saleStatus,
			@RequestParam(defaultValue = "ALL") AuthorGenderFilter authorGender,
			@RequestParam(required = false) List<Long> categoryIds,
			@RequestParam(required = false) Integer heightMin,
			@RequestParam(required = false) Integer heightMax,
			@RequestParam(required = false) Integer weightMin,
			@RequestParam(required = false) Integer weightMax,
			@RequestParam(defaultValue = "RECOMMENDED") PostSortType sort,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		PostSearchCriteria criteria = new PostSearchCriteria(
				keyword, saleStatus, authorGender, categoryIds, heightMin, heightMax, weightMin, weightMax, sort);

		PostSearchResult result = searchService.searchPosts(authUser.id(), criteria, cursorCodec.decode(cursor), size);
		CursorResponse<?> base = cursorCodec.toResponse(result.slice());

		SearchPostListResponse response = SearchPostListResponse.builder()
				.content(result.slice().getContent())
				.nextCursor(base.getNextCursor())
				.hasNext(base.getHasNext())
				.totalCount(result.totalCount())
				.build();

		return ResponseEntity.ok(BaseResponse.success(response));
	}

	@Override
	@GetMapping("/users")
	public ResponseEntity<BaseResponse<CursorResponse<UserSummaryResponse>>> searchUsers(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestParam String keyword,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		CursorResponse<UserSummaryResponse> response = cursorCodec.toResponse(
				searchService.searchUsers(authUser.id(), keyword, cursorCodec.decode(cursor), size));
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	@Override
	@GetMapping("/recommendations")
	public ResponseEntity<BaseResponse<RecommendationResponse>> getRecommendations(
			@AuthenticationPrincipal AuthUser authUser
	) {
		return ResponseEntity.ok(BaseResponse.success(searchService.getRecommendations(authUser.id())));
	}

	@Override
	@GetMapping("/suggested-keywords")
	public ResponseEntity<BaseResponse<List<SuggestedKeywordResponse>>> getSuggestedKeywords(
			@AuthenticationPrincipal AuthUser authUser
	) {
		return ResponseEntity.ok(BaseResponse.success(searchService.getSuggestedKeywords()));
	}

	@Override
	@GetMapping("/recommendations/{section}")
	public ResponseEntity<BaseResponse<?>> getRecommendationSection(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable RecommendationSection section,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		CursorResponse<?> response = cursorCodec.toResponse(
				searchService.getRecommendationSection(authUser.id(), section, cursorCodec.decode(cursor), size));
		return ResponseEntity.ok(BaseResponse.success(response));
	}
}
