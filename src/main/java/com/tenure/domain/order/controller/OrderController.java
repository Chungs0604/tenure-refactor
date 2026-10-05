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
import com.tenure.domain.order.service.OrderService;
import com.tenure.global.pagination.CursorCodec;
import com.tenure.global.pagination.CursorResponse;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController implements OrderApi {

	private final OrderService orderService;

	/** 커서를 문자열로 주고받는 일은 전송 형식이라 여기서 처리한다 — Service 는 정렬 키만 안다 */
	private final CursorCodec cursorCodec;

	// 거래 상세
	@Override
	@GetMapping("/{orderId}")
	public ResponseEntity<BaseResponse<OrderDetailResponse>> getDetail(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long orderId
	) {
		OrderDetailResponse response = orderService.getDetail(authUser.id(), orderId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 판매 내역
	@Override
	@GetMapping("/sales")
	public ResponseEntity<BaseResponse<CursorResponse<OrderSalesRowResponse>>> getSales(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(defaultValue = "ALL") OrderFeedTab tab,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		CursorSlice<OrderSalesRowResponse> slice =
				orderService.getSales(authUser.id(), tab, cursorCodec.decode(cursor), size);
		return ResponseEntity.ok(BaseResponse.success(cursorCodec.toResponse(slice)));
	}

	// 구매 내역
	@Override
	@GetMapping("/purchases")
	public ResponseEntity<BaseResponse<CursorResponse<OrderPurchaseRowResponse>>> getPurchases(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(defaultValue = "ALL") OrderFeedTab tab,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		CursorSlice<OrderPurchaseRowResponse> slice =
				orderService.getPurchases(authUser.id(), tab, cursorCodec.decode(cursor), size);
		return ResponseEntity.ok(BaseResponse.success(cursorCodec.toResponse(slice)));
	}

	// 운송장 번호 입력 → 상품 발송
	@Override
	@PatchMapping("/{orderId}/shipment")
	public ResponseEntity<BaseResponse<ShipmentUpdateResponse>> updateShipment(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long orderId,
			@RequestBody ShipmentUpdateRequest request
	) {
		ShipmentUpdateResponse response = orderService.updateShipment(
				authUser.id(), orderId, request.getCourierCode(), request.getTrackingNo());
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 배송 완료 처리
	@Override
	@PostMapping("/{orderId}/deliver")
	public ResponseEntity<BaseResponse<DeliverResponse>> deliver(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long orderId
	) {
		DeliverResponse response = orderService.deliver(authUser.id(), orderId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 구매 확정 → 소유권 승계
	@Override
	@PostMapping("/{orderId}/confirm")
	public ResponseEntity<BaseResponse<ConfirmResponse>> confirm(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long orderId
	) {
		ConfirmResponse response = orderService.confirm(authUser.id(), orderId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 거래 취소 → 환불
	@Override
	@PostMapping("/{orderId}/cancel")
	public ResponseEntity<BaseResponse<CancelResponse>> cancel(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long orderId
	) {
		CancelResponse response = orderService.cancel(authUser.id(), orderId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 택배사 목록
	@Override
	@GetMapping("/couriers")
	public ResponseEntity<BaseResponse<List<CourierResponse>>> getCouriers(
			@AuthenticationPrincipal AuthUser authUser
	) {
		List<CourierResponse> response = orderService.getCouriers();
		return ResponseEntity.ok(BaseResponse.success(response));
	}
}
