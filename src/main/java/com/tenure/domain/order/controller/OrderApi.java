package com.tenure.domain.order.controller;

import com.tenure.domain.order.dto.request.ShipmentUpdateRequest;
import com.tenure.domain.order.dto.response.CancelResponse;
import com.tenure.domain.order.dto.response.ConfirmResponse;
import com.tenure.domain.order.dto.response.CourierResponse;
import com.tenure.domain.order.dto.response.DeliverResponse;
import com.tenure.domain.order.dto.response.OrderDetailResponse;
import com.tenure.domain.order.dto.response.OrderPurchaseRowResponse;
import com.tenure.domain.order.dto.response.OrderSalesRowResponse;
import com.tenure.domain.order.dto.response.ShipmentUpdateResponse;
import com.tenure.domain.order.enums.OrderFeedTab;
import com.tenure.global.pagination.CursorResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/**
 * 파라미터 제약은 이 인터페이스에만 선언한다. 목록은 커서 방식이고 정렬은 서버가 고정(createdAt 최신순)한다.
 */
@Validated
@Tag(name = "거래", description = "거래 상세·배송·구매확정·취소·내역 API")
public interface OrderApi {

	@Operation(summary = "거래 상세",
			description = "타임라인·배송·결제·정산. 구매자·판매자 정보는 뷰어 역할과 무관하게 둘 다 내려가고, "
					+ "settlement(정산 계좌)만 SELLER 시점에만 채워진다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "403", description = "ORDER_403"),
			@ApiResponse(responseCode = "404", description = "ORDER_404")
	})
	ResponseEntity<BaseResponse<OrderDetailResponse>> getDetail(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "거래 id") Long orderId);

	@Operation(summary = "판매 내역",
			description = "전체/대기중/거래중/거래완료. 대기중은 PENDING 제안을 아이템 단위로 묶어 "
					+ "pendingOfferCount 로 건수를 담는다. rowType 유니온으로 제안·거래를 한 목록에 섞는다(DEC-103)")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "400", description = "COMMON_400 — 커서 형식 오류 또는 size 범위 초과")
	})
	ResponseEntity<BaseResponse<CursorResponse<OrderSalesRowResponse>>> getSales(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "탭. 기본 ALL", example = "ALL") OrderFeedTab tab,
			@Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 비운다") String cursor,
			@Parameter(description = "가져올 개수. 기본 20, 최대 100", example = "20")
			@Min(1) @Max(100) int size);

	@Operation(summary = "구매 내역",
			description = "전체/대기중/거래중/거래완료. 판매 내역과 대칭이나 아이템 단위 집계가 없다 "
					+ "(구매자는 아이템당 1건). 대기중 외 상태(거절·만료)도 전체 탭에서 보인다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "400", description = "COMMON_400 — 커서 형식 오류 또는 size 범위 초과")
	})
	ResponseEntity<BaseResponse<CursorResponse<OrderPurchaseRowResponse>>> getPurchases(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "탭. 기본 ALL", example = "ALL") OrderFeedTab tab,
			@Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 비운다") String cursor,
			@Parameter(description = "가져올 개수. 기본 20, 최대 100", example = "20")
			@Min(1) @Max(100) int size);

	@Operation(summary = "운송장 번호 입력 → 상품 발송 (판매자)",
			description = "배송 완료 전까지 재입력할 수 있다 — 이전 번호는 남기지 않는다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "입력 성공"),
			@ApiResponse(responseCode = "400", description = "ORDER_400_TRACKING"),
			@ApiResponse(responseCode = "403", description = "ORDER_403"),
			@ApiResponse(responseCode = "404", description = "ORDER_404_TRACKING"),
			@ApiResponse(responseCode = "409", description = "ORDER_409_STATUS")
	})
	ResponseEntity<BaseResponse<ShipmentUpdateResponse>> updateShipment(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "거래 id") Long orderId,
			ShipmentUpdateRequest request);

	@Operation(summary = "배송 완료 처리 (판매자)",
			description = "DELIVERED 로 전이하고 +72h 뒤 자동 구매 확정을 건다. 되돌릴 수 없다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "처리 성공"),
			@ApiResponse(responseCode = "403", description = "ORDER_403"),
			@ApiResponse(responseCode = "409", description = "ORDER_409_STATUS")
	})
	ResponseEntity<BaseResponse<DeliverResponse>> deliver(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "거래 id") Long orderId);

	@Operation(summary = "구매 확정 (구매자) → 소유권 승계",
			description = "PURCHASE_CONFIRMED 로 전이하고 소유권을 승계한다. 정산 계좌를 스냅샷해 "
					+ "정산 레코드를 생성한다 — 정산 자체(settled_at)는 영업일이 지난 뒤 배치가 채운다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "확정 성공"),
			@ApiResponse(responseCode = "403", description = "ORDER_403"),
			@ApiResponse(responseCode = "409", description = "ORDER_409_STATUS")
	})
	ResponseEntity<BaseResponse<ConfirmResponse>> confirm(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "거래 id") Long orderId);

	@Operation(summary = "거래 취소 (상품 발송 전) → 환불",
			description = "구매자·판매자 둘 다 요청할 수 있다. 상품 발송 전(PAYMENT_COMPLETED)까지만 가능하다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "취소 성공"),
			@ApiResponse(responseCode = "403", description = "ORDER_403"),
			@ApiResponse(responseCode = "409", description = "ORDER_409_CANCEL_WINDOW / PAYMENT_409_REFUND")
	})
	ResponseEntity<BaseResponse<CancelResponse>> cancel(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "거래 id") Long orderId);

	@Operation(summary = "택배사 목록",
			description = "운송장 입력 화면의 Select box 선택지. 택배사는 앱 배포와 무관하게 늘어날 수 있어 API 로 둔다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공")
	})
	ResponseEntity<BaseResponse<List<CourierResponse>>> getCouriers(
			@Parameter(hidden = true) AuthUser authUser);
}
