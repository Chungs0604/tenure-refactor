package com.tenure.domain.item.controller;

import com.tenure.domain.item.dto.request.ItemCreateRequest;
import com.tenure.domain.item.dto.request.ItemOfferAllowedRequest;
import com.tenure.domain.item.dto.request.ItemUpdateRequest;
import com.tenure.domain.item.dto.response.ItemHistoryPostResponse;
import com.tenure.domain.item.dto.response.ItemIdResponse;
import com.tenure.domain.item.dto.response.ItemOwnershipHistoryResponse;
import com.tenure.domain.item.dto.response.ItemRecommendationResponse;
import com.tenure.domain.item.dto.response.ItemResponse;
import com.tenure.domain.item.dto.response.ItemSummaryResponse;
import com.tenure.domain.item.dto.response.TaggableItemResponse;
import com.tenure.domain.item.service.ItemQueryService;
import com.tenure.domain.item.service.ItemService;
import com.tenure.global.pagination.CursorCodec;
import com.tenure.global.pagination.CursorResponse;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController implements ItemApi {

	private final ItemService itemService;
	private final ItemQueryService itemQueryService;

	/** 커서를 문자열로 주고받는 일은 전송 형식이라 여기서 처리한다 — Service 는 정렬 키만 안다. */
	private final CursorCodec cursorCodec;

	// 내가 등록한 아이템 목록
	@Override
	@GetMapping
	public ResponseEntity<BaseResponse<CursorResponse<ItemSummaryResponse>>> getMyItems(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		CursorSlice<ItemSummaryResponse> slice =
				itemQueryService.getMyItems(authUser.id(), keyword, cursorCodec.decode(cursor), size);

		return ResponseEntity.ok(BaseResponse.success(cursorCodec.toResponse(slice)));
	}

	// 아이템 등록
	@Override
	@PostMapping
	public ResponseEntity<BaseResponse<ItemIdResponse>> create(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestBody ItemCreateRequest request
	) {
		ItemIdResponse response = itemService.create(authUser.id(), request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 태그 가능한 내 아이템
	@Override
	@GetMapping("/me/taggable")
	public ResponseEntity<BaseResponse<CursorResponse<TaggableItemResponse>>> getTaggableItems(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		CursorSlice<TaggableItemResponse> slice = itemQueryService.getTaggableItems(
				authUser.id(), keyword, cursorCodec.decode(cursor), size);

		return ResponseEntity.ok(BaseResponse.success(cursorCodec.toResponse(slice)));
	}

	// 아이템 상세 (소유자 시점)
	@Override
	@GetMapping("/{itemId}")
	public ResponseEntity<BaseResponse<ItemResponse>> getItem(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long itemId
	) {
		ItemResponse response = itemQueryService.getItem(authUser.id(), itemId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 아이템 상세 정보 수정 — 시트가 전체 폼이라 PUT 이다
	@Override
	@PutMapping("/{itemId}")
	public ResponseEntity<BaseResponse<ItemIdResponse>> update(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long itemId,
			@RequestBody ItemUpdateRequest request
	) {
		ItemIdResponse response = itemService.update(authUser.id(), itemId, request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 구매 제안 허용 토글 — 상세 수정 시트가 아니라 선택 모달의 항목이다
	@Override
	@PatchMapping("/{itemId}/offer-allowed")
	public ResponseEntity<BaseResponse<ItemIdResponse>> changeOfferAllowed(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long itemId,
			@RequestBody ItemOfferAllowedRequest request
	) {
		ItemIdResponse response =
				itemService.changeOfferAllowed(authUser.id(), itemId, request.getOfferAllowed());
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 아이템 삭제 (soft)
	@Override
	@DeleteMapping("/{itemId}")
	public ResponseEntity<BaseResponse<Void>> delete(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long itemId
	) {
		itemService.delete(authUser.id(), itemId);
		return ResponseEntity.ok(BaseResponse.success());
	}

	// 아이템 히스토리
	@Override
	@GetMapping("/{itemId}/history")
	public ResponseEntity<BaseResponse<CursorResponse<ItemHistoryPostResponse>>> getHistory(
			@PathVariable Long itemId,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		CursorSlice<ItemHistoryPostResponse> slice =
				itemQueryService.getHistory(itemId, cursorCodec.decode(cursor), size);

		return ResponseEntity.ok(BaseResponse.success(cursorCodec.toResponse(slice)));
	}

	// Tenure 기록 기준 (소유 구간 + 인증 횟수)
	@Override
	@GetMapping("/{itemId}/ownerships")
	public ResponseEntity<BaseResponse<ItemOwnershipHistoryResponse>> getOwnerships(
			@PathVariable Long itemId
	) {
		ItemOwnershipHistoryResponse response = itemQueryService.getOwnerships(itemId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 아이템 기준 추천
	@Override
	@GetMapping("/{itemId}/recommendations")
	public ResponseEntity<BaseResponse<List<ItemRecommendationResponse>>> getRecommendations(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long itemId
	) {
		List<ItemRecommendationResponse> response =
				itemQueryService.getRecommendations(authUser.id(), itemId);

		return ResponseEntity.ok(BaseResponse.success(response));
	}
}
