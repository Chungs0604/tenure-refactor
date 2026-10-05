package com.tenure.domain.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tenure.domain.order.dto.request.ShipmentUpdateRequest;
import com.tenure.domain.order.dto.response.CancelResponse;
import com.tenure.domain.order.dto.response.ConfirmResponse;
import com.tenure.domain.order.dto.response.CourierResponse;
import com.tenure.domain.order.dto.response.DeliverResponse;
import com.tenure.domain.order.dto.response.OrderDetailResponse;
import com.tenure.domain.order.dto.response.OrderPurchaseRowResponse;
import com.tenure.domain.order.dto.response.OrderSalesRowResponse;
import com.tenure.domain.order.dto.response.ShipmentUpdateResponse;
import com.tenure.domain.order.enums.CourierCode;
import com.tenure.domain.order.enums.OrderFeedTab;
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.order.enums.OrderViewerRole;
import com.tenure.domain.order.exception.OrderErrorCode;
import com.tenure.domain.order.service.OrderService;
import com.tenure.global.exception.CustomException;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorCodec;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.security.AuthUser;
import com.tenure.support.ControllerSliceTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(OrderController.class)
@Import(CursorCodec.class)
@DisplayName("OrderController")
class OrderControllerTest extends ControllerSliceTestSupport {

	private static final String ORDERS_URL = "/api/v1/orders";
	private static final Long USER_ID = 1L;
	private static final Long ORDER_ID = 10L;

	@MockitoBean private OrderService orderService;

	@Nested
	@DisplayName("GET /api/v1/orders/{orderId}")
	class GetDetail {

		@Test
		@DisplayName("조회에 성공하면 거래 상세를 내려준다")
		void getDetail_authenticated_returnsDetail() throws Exception {
			given(orderService.getDetail(USER_ID, ORDER_ID)).willReturn(detailResponse());

			mvc.perform(get(ORDERS_URL + "/" + ORDER_ID).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status").value("SHIPPED"))
					.andExpect(jsonPath("$.data.viewerRole").value("BUYER"));
		}

		@Test
		@DisplayName("관련 없는 거래면 403 이다")
		void getDetail_forbidden_returns403() throws Exception {
			willThrow(new CustomException(OrderErrorCode.ORDER_403))
					.given(orderService).getDetail(USER_ID, ORDER_ID);

			mvc.perform(get(ORDERS_URL + "/" + ORDER_ID).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("ORDER_403"));
		}

		@Test
		@DisplayName("없는 거래면 404 다")
		void getDetail_unknownOrder_returns404() throws Exception {
			willThrow(new CustomException(OrderErrorCode.ORDER_404))
					.given(orderService).getDetail(USER_ID, ORDER_ID);

			mvc.perform(get(ORDERS_URL + "/" + ORDER_ID).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("ORDER_404"));
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void getDetail_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(get(ORDERS_URL + "/" + ORDER_ID))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("COMMON_401"));

			verifyNoInteractions(orderService);
		}
	}

	@Nested
	@DisplayName("GET /api/v1/orders/sales")
	class GetSales {

		@Test
		@DisplayName("기본값은 ALL 탭·size 20 이다")
		void getSales_noParams_usesDefaults() throws Exception {
			given(orderService.getSales(USER_ID, OrderFeedTab.ALL, null, 20))
					.willReturn(CursorSlice.empty());

			mvc.perform(get(ORDERS_URL + "/sales").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.hasNext").value(false));

			verify(orderService).getSales(USER_ID, OrderFeedTab.ALL, null, 20);
		}

		@Test
		@DisplayName("받은 커서를 해독해 서비스에 값으로 넘긴다")
		void getSales_withCursor_decodesBeforeService() throws Exception {
			Cursor cursor = Cursor.of(LocalDateTime.of(2026, 6, 21, 0, 0), 99L);
			String encoded = new CursorCodec().encode(cursor);
			given(orderService.getSales(USER_ID, OrderFeedTab.PENDING, cursor, 5))
					.willReturn(CursorSlice.empty());

			mvc.perform(get(ORDERS_URL + "/sales")
							.param("tab", "PENDING")
							.param("cursor", encoded)
							.param("size", "5")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk());

			verify(orderService).getSales(USER_ID, OrderFeedTab.PENDING, cursor, 5);
		}

		@Test
		@DisplayName("깨진 커서는 400 이다")
		void getSales_malformedCursor_returnsBadRequest() throws Exception {
			mvc.perform(get(ORDERS_URL + "/sales").param("cursor", "!!broken!!")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON_400"));

			verifyNoInteractions(orderService);
		}

		@Test
		@DisplayName("size 가 0 이면 400 이다")
		void getSales_sizeZero_returnsBadRequest() throws Exception {
			mvc.perform(get(ORDERS_URL + "/sales").param("size", "0")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isBadRequest());

			verifyNoInteractions(orderService);
		}

		@Test
		@DisplayName("size 가 100 을 넘으면 400 이다")
		void getSales_sizeTooLarge_returnsBadRequest() throws Exception {
			mvc.perform(get(ORDERS_URL + "/sales").param("size", "101")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isBadRequest());

			verifyNoInteractions(orderService);
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void getSales_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(get(ORDERS_URL + "/sales"))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(orderService);
		}
	}

	@Nested
	@DisplayName("GET /api/v1/orders/purchases")
	class GetPurchases {

		@Test
		@DisplayName("기본값은 ALL 탭·size 20 이다")
		void getPurchases_noParams_usesDefaults() throws Exception {
			given(orderService.getPurchases(USER_ID, OrderFeedTab.ALL, null, 20))
					.willReturn(CursorSlice.empty());

			mvc.perform(get(ORDERS_URL + "/purchases").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk());

			verify(orderService).getPurchases(USER_ID, OrderFeedTab.ALL, null, 20);
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void getPurchases_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(get(ORDERS_URL + "/purchases"))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(orderService);
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/orders/{orderId}/shipment")
	class UpdateShipment {

		@Test
		@DisplayName("입력에 성공하면 SHIPPED 를 내려준다")
		void updateShipment_validRequest_returnsShipped() throws Exception {
			given(orderService.updateShipment(USER_ID, ORDER_ID, CourierCode.CJ_LOGISTICS.name(), "1234123412349"))
					.willReturn(ShipmentUpdateResponse.builder().status(OrderStatus.SHIPPED).build());

			mvc.perform(patch(ORDERS_URL + "/" + ORDER_ID + "/shipment")
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(ShipmentUpdateRequest.builder()
									.courierCode(CourierCode.CJ_LOGISTICS.name())
									.trackingNo("1234123412349")
									.build())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status").value("SHIPPED"));
		}

		@Test
		@DisplayName("운송장 번호 형식이 틀리면 400 이다")
		void updateShipment_badTrackingFormat_returns400() throws Exception {
			willThrow(new CustomException(OrderErrorCode.ORDER_400_TRACKING))
					.given(orderService).updateShipment(USER_ID, ORDER_ID, CourierCode.CJ_LOGISTICS.name(), "abc");

			mvc.perform(patch(ORDERS_URL + "/" + ORDER_ID + "/shipment")
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(ShipmentUpdateRequest.builder()
									.courierCode(CourierCode.CJ_LOGISTICS.name())
									.trackingNo("abc")
									.build())))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("ORDER_400_TRACKING"));
		}

		@Test
		@DisplayName("판매자가 아니면 403 이다")
		void updateShipment_notSeller_returns403() throws Exception {
			willThrow(new CustomException(OrderErrorCode.ORDER_403))
					.given(orderService).updateShipment(USER_ID, ORDER_ID, CourierCode.CJ_LOGISTICS.name(), "1234123412349");

			mvc.perform(patch(ORDERS_URL + "/" + ORDER_ID + "/shipment")
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(ShipmentUpdateRequest.builder()
									.courierCode(CourierCode.CJ_LOGISTICS.name())
									.trackingNo("1234123412349")
									.build())))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("ORDER_403"));
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void updateShipment_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(patch(ORDERS_URL + "/" + ORDER_ID + "/shipment")
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(ShipmentUpdateRequest.builder()
									.courierCode(CourierCode.CJ_LOGISTICS.name())
									.trackingNo("1234123412349")
									.build())))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(orderService);
		}
	}

	@Nested
	@DisplayName("POST /api/v1/orders/{orderId}/deliver")
	class Deliver {

		@Test
		@DisplayName("처리에 성공하면 DELIVERED 와 자동확정 시각을 내려준다")
		void deliver_validRequest_returnsDelivered() throws Exception {
			LocalDateTime autoConfirmAt = LocalDateTime.of(2026, 6, 4, 10, 0);
			given(orderService.deliver(USER_ID, ORDER_ID)).willReturn(
					DeliverResponse.builder().status(OrderStatus.DELIVERED).autoConfirmAt(autoConfirmAt).build());

			mvc.perform(post(ORDERS_URL + "/" + ORDER_ID + "/deliver").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status").value("DELIVERED"));
		}

		@Test
		@DisplayName("발송 전이면 409 다")
		void deliver_notShipped_returns409() throws Exception {
			willThrow(new CustomException(OrderErrorCode.ORDER_409_STATUS))
					.given(orderService).deliver(USER_ID, ORDER_ID);

			mvc.perform(post(ORDERS_URL + "/" + ORDER_ID + "/deliver").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("ORDER_409_STATUS"));
		}
	}

	@Nested
	@DisplayName("POST /api/v1/orders/{orderId}/confirm")
	class Confirm {

		@Test
		@DisplayName("확정에 성공하면 PURCHASE_CONFIRMED 와 정산 예정일을 내려준다")
		void confirm_validRequest_returnsConfirmed() throws Exception {
			given(orderService.confirm(USER_ID, ORDER_ID)).willReturn(
					ConfirmResponse.builder()
							.status(OrderStatus.PURCHASE_CONFIRMED)
							.settlementScheduledAt(LocalDate.of(2026, 6, 4))
							.build());

			mvc.perform(post(ORDERS_URL + "/" + ORDER_ID + "/confirm").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status").value("PURCHASE_CONFIRMED"))
					.andExpect(jsonPath("$.data.settlementScheduledAt").value("2026-06-04"));
		}

		@Test
		@DisplayName("구매자가 아니면 403 이다")
		void confirm_notBuyer_returns403() throws Exception {
			willThrow(new CustomException(OrderErrorCode.ORDER_403))
					.given(orderService).confirm(USER_ID, ORDER_ID);

			mvc.perform(post(ORDERS_URL + "/" + ORDER_ID + "/confirm").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("ORDER_403"));
		}
	}

	@Nested
	@DisplayName("POST /api/v1/orders/{orderId}/cancel")
	class Cancel {

		@Test
		@DisplayName("취소에 성공하면 CANCELED 를 내려준다")
		void cancel_validRequest_returnsCanceled() throws Exception {
			given(orderService.cancel(USER_ID, ORDER_ID)).willReturn(
					CancelResponse.builder().status(OrderStatus.CANCELED).refundedAt(LocalDateTime.now()).build());

			mvc.perform(post(ORDERS_URL + "/" + ORDER_ID + "/cancel").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status").value("CANCELED"));
		}

		@Test
		@DisplayName("발송 이후면 409 다")
		void cancel_afterShipped_returns409() throws Exception {
			willThrow(new CustomException(OrderErrorCode.ORDER_409_CANCEL_WINDOW))
					.given(orderService).cancel(USER_ID, ORDER_ID);

			mvc.perform(post(ORDERS_URL + "/" + ORDER_ID + "/cancel").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("ORDER_409_CANCEL_WINDOW"));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/orders/couriers")
	class GetCouriers {

		@Test
		@DisplayName("택배사 목록을 배열로 내려준다")
		void getCouriers_authenticated_returnsArray() throws Exception {
			given(orderService.getCouriers()).willReturn(List.of(
					CourierResponse.builder().code(CourierCode.CJ_LOGISTICS).name("CJ대한통운").build()));

			mvc.perform(get(ORDERS_URL + "/couriers").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].code").value("CJ_LOGISTICS"))
					.andExpect(jsonPath("$.data[0].name").value("CJ대한통운"));
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void getCouriers_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(get(ORDERS_URL + "/couriers"))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(orderService);
		}
	}

	private static UsernamePasswordAuthenticationToken principalOf(Long userId) {
		return new UsernamePasswordAuthenticationToken(new AuthUser(userId), null, List.of());
	}

	private static OrderDetailResponse detailResponse() {
		return OrderDetailResponse.builder()
				.id(ORDER_ID)
				.orderNo("2026042919502395")
				.status(OrderStatus.SHIPPED)
				.viewerRole(OrderViewerRole.BUYER)
				.statusHistory(List.of())
				.itemPrice("360000")
				.shippingFee("5000")
				.feeAmount("0")
				.totalPaymentAmount("365000")
				.payoutAmount("365000")
				.build();
	}
}
