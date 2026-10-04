package com.tenure.domain.offer.controller;

import com.tenure.domain.offer.dto.response.OfferAcceptResponse;
import com.tenure.domain.offer.dto.response.OfferDetailResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
@Tag(name = "제안서", description = "제안서 상세·취소·수락·거절 API")
public interface OfferApi {

	@Operation(summary = "제안서 상세",
			description = "구매자·판매자 시점에 따라 응답이 갈림 — 구매자는 결제 정보(총 결제 금액), "
					+ "판매자는 정산 정보(총 정산 예정 금액)를 봄")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "403", description = "OFFER_403 — 본인과 관련된 제안이 아님"),
			@ApiResponse(responseCode = "404", description = "OFFER_404")
	})
	ResponseEntity<BaseResponse<OfferDetailResponse>> getDetail(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "제안 id") Long offerId);

	@Operation(summary = "제안 취소 (구매자)",
			description = "결제 승인을 해제함. 취소한 제안의 기회는 복구되지 않음")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "취소 성공"),
			@ApiResponse(responseCode = "403", description = "OFFER_403"),
			@ApiResponse(responseCode = "409", description = "OFFER_409_NOT_PENDING"),
			@ApiResponse(responseCode = "410", description = "OFFER_410_EXPIRED")
	})
	ResponseEntity<BaseResponse<Void>> cancel(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "제안 id") Long offerId);

	@Operation(summary = "제안 수락 (판매자) → 거래 생성",
			description = "결제를 확정하고 거래를 생성함. 같은 아이템의 나머지 대기 제안을 전부 "
					+ "자동 취소하고 각 승인을 해제한 뒤 알림을 보내며, 자동 취소된 제안자의 기회는 복구됨. "
					+ "미판매 제안이면 판매자(현재 소유자)의 정산 계좌 등록 여부도 함께 확인함")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "수락 성공"),
			@ApiResponse(responseCode = "400", description = "PAYOUT_400_REQUIRED — 미판매 제안인데 정산 계좌가 없음"),
			@ApiResponse(responseCode = "403", description = "OFFER_403"),
			@ApiResponse(responseCode = "409", description = "OFFER_409_NOT_PENDING / PAYMENT_409_CAPTURE / LISTING_409_SOLD"),
			@ApiResponse(responseCode = "410", description = "OFFER_410_EXPIRED")
	})
	ResponseEntity<BaseResponse<OfferAcceptResponse>> accept(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "제안 id") Long offerId);

	@Operation(summary = "제안 거절 (판매자)",
			description = "결제 승인을 해제함. 거절한 제안은 다시 수락할 수 없음")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "거절 성공"),
			@ApiResponse(responseCode = "403", description = "OFFER_403"),
			@ApiResponse(responseCode = "409", description = "OFFER_409_NOT_PENDING")
	})
	ResponseEntity<BaseResponse<Void>> reject(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "제안 id") Long offerId);
}
