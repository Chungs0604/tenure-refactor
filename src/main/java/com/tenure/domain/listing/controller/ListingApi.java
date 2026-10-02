package com.tenure.domain.listing.controller;

import com.tenure.domain.listing.dto.request.ListingCreateRequest;
import com.tenure.domain.listing.dto.response.ListingIdResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
@Tag(name = "판매 등록", description = "판매 조건 수정 API")
public interface ListingApi {

	@Operation(summary = "판매 정보 수정",
			description = "**폼 전체를 교체한다.** 수정 화면이 판매 전환과 같은 전체 폼이고 헤더도 "
					+ "'판매 전환' 그대로다 — CTA 문구만 '수정하기' 로 바뀐다. 아이템 정보·실측·상태 "
					+ "이상·대표 OOTD 까지 함께 갈아끼운다. 행을 새로 만들지 않고 이 행을 덮어쓴다 — "
					+ "조건을 바꾸는 것이지 다시 파는 것이 아니다. 이미 전송된 제안의 금액은 바뀌지 않는다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "수정 성공"),
			@ApiResponse(responseCode = "400", description = "LISTING_400_FEATURED_RANGE / ITEM_400_MEASUREMENT / ITEM_400_SIZE"),
			@ApiResponse(responseCode = "403", description = "LISTING_403 / LISTING_403_FEE_BEARER"),
			@ApiResponse(responseCode = "404", description = "LISTING_404 / POST_404 / CATEGORY_404"),
			@ApiResponse(responseCode = "409", description = "LISTING_409_CLOSED — 판매가 끝난 등록")
	})
	ResponseEntity<BaseResponse<ListingIdResponse>> update(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "판매 등록 id") Long listingId,
			@Valid ListingCreateRequest request);
}
