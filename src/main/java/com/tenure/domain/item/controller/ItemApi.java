package com.tenure.domain.item.controller;

import com.tenure.domain.item.dto.request.ItemCreateRequest;
import com.tenure.domain.item.dto.request.ItemUpdateRequest;
import com.tenure.domain.item.dto.response.ItemHistoryPostResponse;
import com.tenure.domain.item.dto.response.ItemIdResponse;
import com.tenure.domain.item.dto.response.ItemOwnershipHistoryResponse;
import com.tenure.domain.item.dto.response.ItemRecommendationResponse;
import com.tenure.domain.item.dto.response.ItemResponse;
import com.tenure.domain.item.dto.response.ItemSummaryResponse;
import com.tenure.global.pagination.CursorResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/**
 * 파라미터 제약을 이 인터페이스에만 선언한다 — 근거는 UserApi 의 주석에 적어 두었다.
 *
 * 목록은 커서 방식이다. cursor 는 직전 응답의 nextCursor 를 그대로 넘기고 첫 페이지에서는 비운다.
 *
 * 목록과 상세는 등록자가 아니라 현재 소유자 기준이다 — 판 아이템은 목록에서 빠지고 상세도
 * 열리지 않는다. 그래야 남의 옷을 수정하는 경로가 생기지 않는다.
 */
@Validated
@Tag(name = "아이템", description = "아이템 등록·조회 API")
public interface ItemApi {

	@Operation(summary = "내가 등록한 아이템 목록",
			description = "현재 보유 중인 아이템만 내려간다. 검색어는 브랜드명·아이템명 부분 일치다. "
					+ "판매 상태는 아이템 레벨로 평탄화한 값이며 판매 등록이 없으면 NOT_FOR_SALE 이다. "
					+ "정렬은 등록일 최신순 고정이며 커서 키도 등록일이다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "400", description = "COMMON_400 — 커서 형식 오류 또는 size 범위 초과")
	})
	ResponseEntity<BaseResponse<CursorResponse<ItemSummaryResponse>>> getMyItems(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "브랜드명·아이템명 부분 일치. 2자 이상", example = "Levis")
			@Size(min = 2) String keyword,
			@Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 비운다") String cursor,
			@Parameter(description = "가져올 개수. 기본 20, 최대 100", example = "20")
			@Min(1) @Max(100) int size);

	@Operation(summary = "아이템 등록",
			description = "아이템과 최초 소유 구간을 함께 만든다. 소유 구간이 있어야 게시물에 태그할 수 있다. "
					+ "최초 보유 날짜를 비우면 소유 구간은 등록일부터 연다. "
					+ "사이즈는 카테고리 트리가 준 label 만 받는다. 실측·상태 이상은 판매 전환에서 받는다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "등록 성공"),
			@ApiResponse(responseCode = "400", description = "ITEM_400_CATEGORY — 세부 카테고리가 아님 / "
					+ "ITEM_400_SIZE — 카테고리에 없는 사이즈"),
			@ApiResponse(responseCode = "404", description = "CATEGORY_404 — 카테고리를 찾을 수 없음")
	})
	ResponseEntity<BaseResponse<ItemIdResponse>> create(
			@Parameter(hidden = true) AuthUser authUser,
			@Valid ItemCreateRequest request);

	@Operation(summary = "아이템 상세 (소유자 시점)",
			description = "소유 구간 카드와 '자주 같이 입은 옷' 을 함께 준다. "
					+ "하단 CTA 는 saleStatus 로 갈린다 — 판매 중이면 판매 페이지, 그 외에는 공개 페이지다. "
					+ "현재 소유자만 열 수 있다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "403", description = "ITEM_403 — 본인의 아이템이 아님"),
			@ApiResponse(responseCode = "404", description = "ITEM_404 — 아이템을 찾을 수 없음")
	})
	ResponseEntity<BaseResponse<ItemResponse>> getItem(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "아이템 id") Long itemId);

	@Operation(summary = "아이템 상세 정보 수정",
			description = "보낸 항목만 바뀐다. 최초 보유 날짜를 바꾸면 최초 소유 구간의 시작일도 함께 옮긴다 — "
					+ "소유 구간이 하나일 때만 허용하며 거래 이력이 생기면 잠긴다. "
					+ "구매 제안 허용 토글도 여기서 바꾼다 — 미판매 아이템에는 판매 등록 행이 없다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "수정 성공"),
			@ApiResponse(responseCode = "400", description = "ITEM_400_CATEGORY / ITEM_400_SIZE"),
			@ApiResponse(responseCode = "403", description = "ITEM_403 — 본인의 아이템이 아님"),
			@ApiResponse(responseCode = "404", description = "ITEM_404 / CATEGORY_404"),
			@ApiResponse(responseCode = "409", description = "ITEM_409_TRADED — 거래 이력이 있어 최초 보유 날짜를 바꿀 수 없음")
	})
	ResponseEntity<BaseResponse<ItemIdResponse>> update(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "아이템 id") Long itemId,
			@Valid ItemUpdateRequest request);

	@Operation(summary = "아이템 삭제",
			description = "soft delete. 태그는 남아 '삭제된 아이템' 으로 표시된다. "
					+ "판매 중이거나 끝나지 않은 거래가 있으면 막는다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "삭제 성공"),
			@ApiResponse(responseCode = "403", description = "ITEM_403 — 본인의 아이템이 아님"),
			@ApiResponse(responseCode = "404", description = "ITEM_404 — 아이템을 찾을 수 없음"),
			@ApiResponse(responseCode = "409", description = "ITEM_409_ON_SALE — 판매 중이거나 거래 진행 중")
	})
	ResponseEntity<BaseResponse<Void>> delete(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "아이템 id") Long itemId);

	@Operation(summary = "아이템 히스토리",
			description = "이 아이템이 등장한 OOTD 사진 그리드. 무효화된 인증과 삭제된 게시물은 빠진다. "
					+ "masonry 2열이라 aspectRatio 를 함께 싣는다. "
					+ "소유 구간 카드의 '더보기' 로 들어오지만 아이템 전체를 보여준다. "
					+ "정렬은 착용일 최신순 고정이며 커서 키도 착용일이다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "404", description = "ITEM_404 — 아이템을 찾을 수 없음")
	})
	ResponseEntity<BaseResponse<CursorResponse<ItemHistoryPostResponse>>> getHistory(
			@Parameter(description = "아이템 id") Long itemId,
			@Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 비운다") String cursor,
			@Parameter(description = "가져올 개수. 기본 20, 최대 100", example = "20")
			@Min(1) @Max(100) int size);

	@Operation(summary = "Tenure 기록 기준",
			description = "소유 구간과 구간별 인증 횟수. 최초 구간에만 '최초 등록' 배지가 붙는다. "
					+ "구매자 시점의 상품 상세도 같은 섹션을 쓰므로 소유자가 아니어도 볼 수 있다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "404", description = "ITEM_404 — 아이템을 찾을 수 없음")
	})
	ResponseEntity<BaseResponse<ItemOwnershipHistoryResponse>> getOwnerships(
			@Parameter(description = "아이템 id") Long itemId);

	@Operation(summary = "태그 가능한 내 아이템",
			description = "현재 소유 구간이 열려 있는 본인 아이템만 내려간다. "
					+ "게시 후 그 아이템을 팔아도 과거 태그는 그대로 남는다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "400", description = "COMMON_400 — 커서 형식 오류 또는 size 범위 초과")
	})
	ResponseEntity<BaseResponse<CursorResponse<ItemSummaryResponse>>> getTaggableItems(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "브랜드명·아이템명 부분 일치") String keyword,
			@Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 비운다") String cursor,
			@Parameter(description = "가져올 개수. 기본 20, 최대 100", example = "20")
			@Min(1) @Max(100) int size);

	@Operation(summary = "아이템 기준 추천",
			description = "태그된 아이템 시트 아래에 붙는 블록 3종. 유사도 모델을 쓰지 않고 "
					+ "카테고리와 공동 태그로 최근 게시물을 모은다. 블록별 최대 10건이며 "
					+ "차단 사용자와 삭제된 게시물·아이템은 빠진다. 비어 있는 블록은 내려가지 않는다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "404", description = "ITEM_404 — 아이템을 찾을 수 없음")
	})
	ResponseEntity<BaseResponse<List<ItemRecommendationResponse>>> getRecommendations(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "기준 아이템 id") Long itemId);
}
