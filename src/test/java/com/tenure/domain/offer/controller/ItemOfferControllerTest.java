package com.tenure.domain.offer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tenure.domain.offer.dto.request.OfferCreateRequest;
import com.tenure.domain.offer.dto.response.OfferCreateResponse;
import com.tenure.domain.offer.dto.response.OfferPreviewResponse;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.payment.enums.PaymentMethod;
import com.tenure.domain.offer.service.OfferService;
import com.tenure.global.security.AuthUser;
import com.tenure.support.ControllerSliceTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(ItemOfferController.class)
@DisplayName("ItemOfferController")
class ItemOfferControllerTest extends ControllerSliceTestSupport {

	private static final Long USER_ID = 1L;
	private static final Long ITEM_ID = 100L;
	private static final String PREVIEW_URL = "/api/v1/items/" + ITEM_ID + "/offer-preview";
	private static final String OFFERS_URL = "/api/v1/items/" + ITEM_ID + "/offers";

	@MockitoBean private OfferService offerService;

	@Test
	@DisplayName("제안 금액이 상한을 넘으면 400 이다")
	void preview_offerPriceOverMax_returnsBadRequest() throws Exception {
		mvc.perform(get(PREVIEW_URL).param("offerPrice", "100000001")
						.with(authentication(principalOf(USER_ID))))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("제안 금액이 1 보다 작으면 400 이다")
	void preview_offerPriceBelowMin_returnsBadRequest() throws Exception {
		mvc.perform(get(PREVIEW_URL).param("offerPrice", "0")
						.with(authentication(principalOf(USER_ID))))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("범위 안의 제안 금액은 그대로 조회한다")
	void preview_offerPriceWithinRange_returnsOk() throws Exception {
		given(offerService.preview(anyLong(), anyInt())).willReturn(OfferPreviewResponse.builder().build());

		mvc.perform(get(PREVIEW_URL).param("offerPrice", "100000000")
						.with(authentication(principalOf(USER_ID))))
				.andExpect(status().isOk());
	}

	@Nested
	@DisplayName("POST /api/v1/items/{itemId}/offers")
	class Create {

		@Test
		@DisplayName("필수값을 모두 채우면 전송한다")
		void create_validRequest_returnsOk() throws Exception {
			given(offerService.create(eq(USER_ID), eq(ITEM_ID), any(OfferCreateRequest.class)))
					.willReturn(OfferCreateResponse.builder().offerId(1L).totalPaymentAmount("100000").build());

			mvc.perform(post(OFFERS_URL)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(createRequest())))
					.andExpect(status().isOk());
		}

		@Test
		@DisplayName("결제 승인 키(paymentKey)가 빈 문자열이면 400 이고 서비스를 부르지 않는다")
		void create_blankPaymentKey_returnsBadRequest() throws Exception {
			OfferCreateRequest request = createRequest("", "a1b2c3d4-e5f6-7890-abcd-ef1234567890");

			mvc.perform(post(OFFERS_URL)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(request)))
					.andExpect(status().isBadRequest());

			verifyNoInteractions(offerService);
		}

		@Test
		@DisplayName("주문 식별자(orderId)가 빈 문자열이면 400 이고 서비스를 부르지 않는다")
		void create_blankOrderId_returnsBadRequest() throws Exception {
			OfferCreateRequest request = createRequest(
					"5EnNZRJGvaBX7zk4Y9bAN97Eoq2D7qgO41Dq2BAkXoYarMAD", "");

			mvc.perform(post(OFFERS_URL)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(request)))
					.andExpect(status().isBadRequest());

			verifyNoInteractions(offerService);
		}

		private OfferCreateRequest createRequest() {
			return createRequest(
					"5EnNZRJGvaBX7zk4Y9bAN97Eoq2D7qgO41Dq2BAkXoYarMAD", "a1b2c3d4-e5f6-7890-abcd-ef1234567890");
		}

		private OfferCreateRequest createRequest(String paymentKey, String orderId) {
			return OfferCreateRequest.builder()
					.offerType(OfferType.PURCHASE_OFFER)
					.offerPrice(360_000)
					.shippingAddressId(1L)
					.paymentMethod(PaymentMethod.TOSS_PAY)
					.paymentKey(paymentKey)
					.orderId(orderId)
					.build();
		}
	}

	private static UsernamePasswordAuthenticationToken principalOf(Long userId) {
		return new UsernamePasswordAuthenticationToken(new AuthUser(userId), null, List.of());
	}
}
