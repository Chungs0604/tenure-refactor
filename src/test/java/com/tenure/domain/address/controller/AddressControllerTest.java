package com.tenure.domain.address.controller;

import com.tenure.domain.address.dto.request.ShippingAddressCreateRequest;
import com.tenure.domain.address.dto.response.ShippingAddressIdResponse;
import com.tenure.domain.address.dto.response.ShippingAddressResponse;
import com.tenure.domain.address.service.AddressService;
import com.tenure.global.security.AuthUser;
import com.tenure.support.ControllerSliceTestSupport;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AddressController.class)
@DisplayName("AddressController")
class AddressControllerTest extends ControllerSliceTestSupport {

	private static final String ADDRESSES_URL = "/api/v1/shipping-addresses";
	private static final Long USER_ID = 1L;
	private static final Long ADDRESS_ID = 10L;

	@MockitoBean private AddressService addressService;

	@Nested
	@DisplayName("GET /api/v1/shipping-addresses")
	class GetAddresses {

		@Test
		@DisplayName("배송지 목록을 배열로 내려준다")
		void getAddresses_authenticated_returnsArray() throws Exception {
			given(addressService.getAddresses(USER_ID)).willReturn(List.of(
					response(ADDRESS_ID, "홍길동", true),
					response(11L, "김유진", false)));

			mvc.perform(get(ADDRESSES_URL).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.length()").value(2))
					.andExpect(jsonPath("$.data[0].recipientName").value("홍길동"))
					.andExpect(jsonPath("$.data[0].zipcode").value("07027"))
					// 래퍼 타입이라야 키가 isDefault 로 나간다
					.andExpect(jsonPath("$.data[0].isDefault").value(true))
					.andExpect(jsonPath("$.data[1].isDefault").value(false));
		}

		@Test
		@DisplayName("배송지가 없으면 빈 배열이다")
		void getAddresses_noAddress_returnsEmptyArray() throws Exception {
			given(addressService.getAddresses(USER_ID)).willReturn(List.of());

			mvc.perform(get(ADDRESSES_URL).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.length()").value(0));
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void getAddresses_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(get(ADDRESSES_URL))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("COMMON_401"));

			verifyNoInteractions(addressService);
		}
	}

	@Nested
	@DisplayName("POST /api/v1/shipping-addresses")
	class Create {

		@Test
		@DisplayName("추가에 성공하면 id 를 내려준다")
		void create_validRequest_returnsId() throws Exception {
			given(addressService.create(eq(USER_ID), any(ShippingAddressCreateRequest.class)))
					.willReturn(ShippingAddressIdResponse.builder().id(ADDRESS_ID).build());

			mvc.perform(post(ADDRESSES_URL)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(createRequest(builder -> {}))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id").value(ADDRESS_ID));
		}

		@Test
		@DisplayName("선택 항목을 비워도 추가된다")
		void create_withoutOptionalFields_returnsOk() throws Exception {
			given(addressService.create(eq(USER_ID), any(ShippingAddressCreateRequest.class)))
					.willReturn(ShippingAddressIdResponse.builder().id(ADDRESS_ID).build());

			mvc.perform(post(ADDRESSES_URL)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(createRequest(builder -> builder
									.addressDetail(null)
									.deliveryRequest(null)
									.isDefault(null)))))
					.andExpect(status().isOk());
		}

		@Test
		@DisplayName("수령인이 비면 400 이고 서비스를 호출하지 않는다")
		void create_blankRecipient_returnsBadRequest() throws Exception {
			expectBadRequest(createRequest(builder -> builder.recipientName(" ")));

			verifyNoInteractions(addressService);
		}

		@Test
		@DisplayName("연락처가 비면 400 이다")
		void create_blankPhone_returnsBadRequest() throws Exception {
			expectBadRequest(createRequest(builder -> builder.phone("")));
		}

		@Test
		@DisplayName("주소가 비면 400 이다")
		void create_blankAddress_returnsBadRequest() throws Exception {
			expectBadRequest(createRequest(builder -> builder.address(" ")));
		}

		@Test
		@DisplayName("우편번호가 비면 400 이다")
		void create_blankZipcode_returnsBadRequest() throws Exception {
			// 주소 검색이 도로명주소와 함께 돌려주므로 화면은 항상 보낼 수 있다
			expectBadRequest(createRequest(builder -> builder.zipcode("")));
		}

		@Test
		@DisplayName("수령인이 컬럼 길이를 넘으면 400 이다")
		void create_tooLongRecipient_returnsBadRequest() throws Exception {
			// 막지 않으면 DB 에서 끊겨 500 이 된다. 잘못된 입력은 400 으로 돌려준다
			expectBadRequest(createRequest(builder -> builder.recipientName("가".repeat(51))));
		}

		private void expectBadRequest(ShippingAddressCreateRequest request) throws Exception {
			mvc.perform(post(ADDRESSES_URL)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.code").value("COMMON_400"));
		}
	}

	private static UsernamePasswordAuthenticationToken principalOf(Long userId) {
		return new UsernamePasswordAuthenticationToken(new AuthUser(userId), null, List.of());
	}

	private static ShippingAddressResponse response(Long id, String recipientName, boolean isDefault) {
		return ShippingAddressResponse.builder()
				.id(id)
				.recipientName(recipientName)
				.phone("010-1234-5678")
				.zipcode("07027")
				.address("서울특별시 동작구 상도로 369")
				.addressDetail("104동 201호")
				.isDefault(isDefault)
				.build();
	}

	/** 유효한 요청을 만들고, 검사하려는 필드 하나만 덮어쓴다. */
	private static ShippingAddressCreateRequest createRequest(
			Consumer<ShippingAddressCreateRequest.ShippingAddressCreateRequestBuilder> override
	) {
		ShippingAddressCreateRequest.ShippingAddressCreateRequestBuilder builder =
				ShippingAddressCreateRequest.builder()
						.recipientName("홍길동")
						.phone("010-1234-5678")
						.zipcode("07027")
						.address("서울특별시 동작구 상도로 369")
						.addressDetail("104동 201호")
						.deliveryRequest("부재 시 문 앞")
						.isDefault(false);

		override.accept(builder);
		return builder.build();
	}
}
