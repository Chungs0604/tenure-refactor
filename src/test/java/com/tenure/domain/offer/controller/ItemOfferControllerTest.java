package com.tenure.domain.offer.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tenure.domain.offer.dto.response.OfferPreviewResponse;
import com.tenure.domain.offer.service.OfferService;
import com.tenure.global.security.AuthUser;
import com.tenure.support.ControllerSliceTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(ItemOfferController.class)
@DisplayName("ItemOfferController")
class ItemOfferControllerTest extends ControllerSliceTestSupport {

	private static final Long USER_ID = 1L;
	private static final Long ITEM_ID = 100L;
	private static final String PREVIEW_URL = "/api/v1/items/" + ITEM_ID + "/offer-preview";

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

	private static UsernamePasswordAuthenticationToken principalOf(Long userId) {
		return new UsernamePasswordAuthenticationToken(new AuthUser(userId), null, List.of());
	}
}
