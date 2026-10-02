package com.tenure.domain.listing.controller;

import com.tenure.domain.listing.dto.request.ListingCreateRequest;
import com.tenure.domain.listing.dto.request.SaleStatusChangeRequest;
import com.tenure.domain.listing.dto.response.FeaturedPostResponse;
import com.tenure.domain.listing.dto.response.ItemDetailResponse;
import com.tenure.domain.listing.dto.response.ListingIdResponse;
import com.tenure.domain.listing.dto.response.SaleStatusResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/**
 * 아이템 경로를 쓰는 판매 관련 API.
 *
 * 경로가 /items 인 것은 화면 때문이다 — 판매중·미판매·판매완료가 한 화면의 상태 변형이고
 * 판매 등록이 없는 아이템에도 공개 페이지가 있어, 진입 키가 listingId 일 수 없다 (DEC-026).
 * 같은 이유로 판매 상태도 item 이 갖는다.
 */
@Validated
@Tag(name = "아이템 판매", description = "아이템 상세(공개 페이지)·판매 전환·판매 상태 API")
public interface ItemListingApi {

	@Operation(summary = "아이템 상세 (공개 페이지)",
			description = "판매중·미판매·판매완료가 한 화면의 상태 변형이다. `listing` 은 **열린 판매 "
					+ "등록이 있을 때만** 채워지고, 없으면 화면이 가격·배송비·수수료 부담·상품 설명·"
					+ "실측·상태 이상 섹션을 통째로 접는다. `featuredPosts` 와 `taggedPosts` 는 "
					+ "배타적이다 — 판매 이력이 있으면 대표 OOTD 를, 한 번도 없었으면 태그된 OOTD 를 "
					+ "그리고 나머지는 빈 배열이다. 소유 구간은 싣지 않는다(소유자 시점 전용).")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "404", description = "ITEM_404 — 없거나 삭제된 아이템")
	})
	ResponseEntity<BaseResponse<ItemDetailResponse>> getItemDetail(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "아이템 id") Long itemId);

	@Operation(summary = "판매 전환",
			description = "**아이템 정보도 함께 갱신한다** — 화면이 대표 사진·브랜드명·아이템명·"
					+ "카테고리·사이즈·착용 대상을 전부 다시 받는 한 폼·한 CTA 다. 정산 계좌가 없으면 "
					+ "거부한다. 기본 사용자는 수수료 부담·배송비를 보낼 수 없다(보내면 403). "
					+ "**항상 새 행을 만든다** — 미판매로 내린 뒤 다시 눌러도 닫힌 행을 되살리지 않는다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "판매 게시 성공"),
			@ApiResponse(responseCode = "400", description = "PAYOUT_400_REQUIRED / LISTING_400_FEATURED_RANGE / ITEM_400_MEASUREMENT / ITEM_400_SIZE"),
			@ApiResponse(responseCode = "403", description = "ITEM_403 / LISTING_403_FEE_BEARER"),
			@ApiResponse(responseCode = "404", description = "ITEM_404 / POST_404 / CATEGORY_404"),
			@ApiResponse(responseCode = "409", description = "LISTING_409_ALREADY_OPEN — 이미 판매 중")
	})
	ResponseEntity<BaseResponse<ListingIdResponse>> create(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "아이템 id") Long itemId,
			@Valid ListingCreateRequest request);

	@Operation(summary = "판매 상태 변경",
			description = "메뉴 모달이 주는 선택지가 '미판매로 변경하기' 와 '판매 완료로 변경하기' "
					+ "둘뿐이라 **ON_SALE 은 받지 않는다** — 다시 팔려면 판매 전환을 거치며 그때 새 "
					+ "판매 등록이 생긴다. 열린 판매 등록이 있으면 함께 닫는다. 거래를 통한 판매 완료는 "
					+ "이 API 가 아니라 결제가 끝나는 시점에 서버가 바꾼다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "변경 성공"),
			@ApiResponse(responseCode = "400", description = "LISTING_400_STATUS — 판매중으로 되돌릴 수 없음"),
			@ApiResponse(responseCode = "403", description = "ITEM_403 — 현재 소유자가 아님"),
			@ApiResponse(responseCode = "404", description = "ITEM_404"),
			@ApiResponse(responseCode = "409", description = "LISTING_409_IN_TRADE — 진행 중인 거래가 있음")
	})
	ResponseEntity<BaseResponse<SaleStatusResponse>> changeSaleStatus(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "아이템 id") Long itemId,
			@Valid SaleStatusChangeRequest request);

	@Operation(summary = "대표 OOTD 전체 보기",
			description = "상세의 '대표 OOTD' 우측 화살표로 들어오는 masonry 그리드다. 열린 판매 "
					+ "등록이 없으면 **가장 최근 판매 등록**의 대표 OOTD 를 보여준다 — 미판매로 내려도 "
					+ "화면에 남는다. 판매 이력이 아예 없으면 빈 배열이다(그 화면은 태그된 OOTD 를 그린다).")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "404", description = "ITEM_404")
	})
	ResponseEntity<BaseResponse<List<FeaturedPostResponse>>> getFeaturedPosts(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "아이템 id") Long itemId);
}
