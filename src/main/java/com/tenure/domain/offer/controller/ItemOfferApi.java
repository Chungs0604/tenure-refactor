package com.tenure.domain.offer.controller;

import com.tenure.domain.offer.dto.request.OfferCreateRequest;
import com.tenure.domain.offer.dto.response.OfferCreateResponse;
import com.tenure.domain.offer.dto.response.OfferPreviewResponse;
import com.tenure.domain.offer.dto.response.OfferReceivedListResponse;
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

/**
 * 아이템 경로를 쓰는 구매 제안 API.
 *
 * 미판매 구매 제안은 가리킬 판매 등록이 없어 listingId 로 열 수 없음 — 판매 중 거래 의사와
 * 한 엔드포인트를 공유하려면 아이템 기준이어야 함
 */
@Validated
@Tag(name = "구매 제안", description = "미리보기·전송·받은 제안 목록 API")
public interface ItemOfferApi {

	@Operation(summary = "결제 금액 미리보기",
			description = "제안을 보내기 전 수수료·배송비를 계산해 금액 4행을 보여줌. 판매 중이면 "
					+ "이미 걸린 판매가·배송비·수수료 부담을 그대로 쓰고, 미판매면 쿼리로 받은 제안 "
					+ "금액을 쓰며 배송비는 항상 0원·수수료는 판매자 전액 부담으로 고정됨. 요율은 "
					+ "소유자 등급 기준 BASIC 6% / RECORD 3%")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "400", description = "미판매 아이템인데 제안 금액을 안 보냄"),
			@ApiResponse(responseCode = "404", description = "ITEM_404")
	})
	ResponseEntity<BaseResponse<OfferPreviewResponse>> preview(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "아이템 id") Long itemId,
			@Parameter(description = "제안 금액. 미판매일 때만 보냄") Integer offerPrice);

	@Operation(summary = "구매 제안 / 거래 의사 전송",
			description = "같은 트랜잭션에서 결제 승인을 임시로 확보하고 24시간 만료를 걺. 미판매 "
					+ "구매 제안은 아이템당 1회만 가능하며, 다른 제안이 수락돼 자동 취소된 이력은 "
					+ "횟수에서 뺌. 미판매 제안이면 같은 구매자·아이템의 채팅방 가격 출처를 이 제안으로 "
					+ "갱신하고, 방이 없으면 새로 염")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "전송 성공"),
			@ApiResponse(responseCode = "400", description = "OFFER_400_NOT_ALLOWED / OFFER_400_SELF / OFFER_400_ADDRESS_REQUIRED"),
			@ApiResponse(responseCode = "402", description = "PAYMENT_402 — 결제 승인 실패"),
			@ApiResponse(responseCode = "403", description = "OFFER_403_BLOCKED — 차단 관계"),
			@ApiResponse(responseCode = "404", description = "ITEM_404"),
			@ApiResponse(responseCode = "409", description = "OFFER_409_ALREADY_USED / LISTING_409_SOLD")
	})
	ResponseEntity<BaseResponse<OfferCreateResponse>> create(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "아이템 id") Long itemId,
			@Valid OfferCreateRequest request);

	@Operation(summary = "아이템별 받은 제안·요청 목록",
			description = "본인 아이템에 쌓인 대기 중 제안을 모아 보여줌. 판매 중이면 '거래 의사', "
					+ "미판매면 '구매 제안' 으로 헤더 문구가 갈리고 만료된 제안은 목록에서 빠짐")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "403", description = "ITEM_403 — 본인 아이템이 아님"),
			@ApiResponse(responseCode = "404", description = "ITEM_404")
	})
	ResponseEntity<BaseResponse<OfferReceivedListResponse>> getReceivedList(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "아이템 id") Long itemId);
}
