package com.tenure.domain.listing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tenure.domain.category.enums.MeasurementField;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.listing.dto.response.ItemDetailResponse;
import com.tenure.domain.listing.dto.response.ListingIdResponse;
import com.tenure.domain.listing.dto.response.ListingTermsResponse;
import com.tenure.domain.listing.dto.response.SaleStatusResponse;
import com.tenure.domain.listing.exception.ListingErrorCode;
import com.tenure.domain.listing.service.ListingQueryService;
import com.tenure.domain.listing.service.ListingService;
import com.tenure.global.exception.CustomException;
import com.tenure.global.security.AuthUser;
import com.tenure.support.ControllerSliceTestSupport;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(ItemListingController.class)
@DisplayName("ItemListingController")
class ItemListingControllerTest extends ControllerSliceTestSupport {

	private static final Long USER_ID = 1L;
	private static final Long ITEM_ID = 100L;
	private static final Long LISTING_ID = 200L;
	private static final String ITEMS_URL = "/api/v1/items";

	@MockitoBean private ListingService listingService;
	@MockitoBean private ListingQueryService listingQueryService;

	@Test
	@DisplayName("판매 전환에 성공하면 판매 등록 id 를 내려준다")
	void create_success_returnsId() throws Exception {
		given(listingService.create(anyLong(), anyLong(), any()))
				.willReturn(ListingIdResponse.builder().id(LISTING_ID).build());

		mvc.perform(post(ITEMS_URL + "/" + ITEM_ID + "/listings")
						.with(authentication(principalOf(USER_ID))).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(LISTING_ID));
	}

	@Test
	@DisplayName("아이템 정보를 빠뜨리면 400 이다 — 폼이 전부 다시 받는다")
	void create_missingItemBlock_returnsBadRequest() throws Exception {
		mvc.perform(post(ITEMS_URL + "/" + ITEM_ID + "/listings")
						.with(authentication(principalOf(USER_ID))).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "price": 320000,
								  "featuredPostIds": [12]
								}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("대표 OOTD 를 하나도 안 보내면 400 이다")
	void create_emptyFeaturedPosts_returnsBadRequest() throws Exception {
		mvc.perform(post(ITEMS_URL + "/" + ITEM_ID + "/listings")
						.with(authentication(principalOf(USER_ID))).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody().replace("\"featuredPostIds\": [12]",
								"\"featuredPostIds\": []")))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("미판매 아이템 상세는 listing 과 seller 가 비고 태그된 OOTD 를 그린다")
	void getItemDetail_neverListed_hasNoListing() throws Exception {
		given(listingQueryService.getItemDetail(anyLong(), anyLong()))
				.willReturn(ItemDetailResponse.builder()
						.itemId(ITEM_ID)
						.brandName("Levis")
						.name("LVC 1955 501")
						.images(List.of())
						.saleStatus(SaleStatus.NOT_FOR_SALE)
						.offerAllowed(true)
						.wishCount(5)
						.isWished(false)
						.categoryName("긴소매 티셔츠")
						.sizeLabel("L")
						.totalWearCount(10)
						.featuredPosts(List.of())
						.taggedPosts(List.of())
						.build());

		mvc.perform(get(ITEMS_URL + "/" + ITEM_ID + "/detail")
						.with(authentication(principalOf(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.saleStatus").value("NOT_FOR_SALE"))
				.andExpect(jsonPath("$.data.listing").doesNotExist())
				.andExpect(jsonPath("$.data.seller").doesNotExist())
				.andExpect(jsonPath("$.data.wishCount").value(5));
	}

	@Test
	@DisplayName("판매 중이면 판매 조건이 함께 내려간다")
	void getItemDetail_onSale_carriesTerms() throws Exception {
		given(listingQueryService.getItemDetail(anyLong(), anyLong()))
				.willReturn(ItemDetailResponse.builder()
						.itemId(ITEM_ID)
						.saleStatus(SaleStatus.ON_SALE)
						.images(List.of())
						.listing(ListingTermsResponse.builder()
								.id(LISTING_ID)
								.price("128000")
								.shippingFee("5000")
								.conditions(List.of())
								.build())
						.featuredPosts(List.of())
						.taggedPosts(List.of())
						.build());

		mvc.perform(get(ITEMS_URL + "/" + ITEM_ID + "/detail")
						.with(authentication(principalOf(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.listing.id").value(LISTING_ID))
				.andExpect(jsonPath("$.data.listing.price").value("128000"));
	}

	@Test
	@DisplayName("실측은 세트에 있는 키만 보낸다 — 안 쓰는 항목이 null 로 끼지 않는다")
	void create_measurementsCarryOnlySchemeKeys() throws Exception {
		given(listingService.create(anyLong(), anyLong(), any()))
				.willReturn(ListingIdResponse.builder().id(LISTING_ID).build());

		mvc.perform(post(ITEMS_URL + "/" + ITEM_ID + "/listings")
						.with(authentication(principalOf(USER_ID))).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody().replace("\"featuredPostIds\": [12]",
								"""
								"featuredPostIds": [12],
								  "measurements": {
								    "SHOULDER_WIDTH": 45.0,
								    "CHEST_WIDTH": 52.0,
								    "SLEEVE_LENGTH": 61.0,
								    "TOTAL_LENGTH": 70.0
								  }""")))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("실측 응답에도 채운 항목만 담긴다")
	void getItemDetail_measurementsHaveNoNulls() throws Exception {
		given(listingQueryService.getItemDetail(anyLong(), anyLong()))
				.willReturn(ItemDetailResponse.builder()
						.itemId(ITEM_ID)
						.saleStatus(SaleStatus.ON_SALE)
						.images(List.of())
						.listing(ListingTermsResponse.builder()
								.id(LISTING_ID)
								.price("128000")
								.shippingFee("5000")
								.measurements(Map.of(
										MeasurementField.SHOULDER_WIDTH, new BigDecimal("45.0"),
										MeasurementField.CHEST_WIDTH, new BigDecimal("52.0")))
								.conditions(List.of())
								.build())
						.featuredPosts(List.of())
						.taggedPosts(List.of())
						.build());

		mvc.perform(get(ITEMS_URL + "/" + ITEM_ID + "/detail")
						.with(authentication(principalOf(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.listing.measurements.SHOULDER_WIDTH").value(45.0))
				.andExpect(jsonPath("$.data.listing.measurements.WAIST_WIDTH").doesNotExist())
				.andExpect(jsonPath("$.data.listing.measurements.length()").value(2));
	}

	@Test
	@DisplayName("판매 상태를 미판매로 바꾸면 바뀐 상태를 내려준다")
	void changeSaleStatus_success() throws Exception {
		given(listingService.changeSaleStatus(anyLong(), anyLong(), any()))
				.willReturn(SaleStatusResponse.builder()
						.itemId(ITEM_ID)
						.saleStatus(SaleStatus.NOT_FOR_SALE)
						.build());

		mvc.perform(patch(ITEMS_URL + "/" + ITEM_ID + "/sale-status")
						.with(authentication(principalOf(USER_ID))).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "saleStatus": "NOT_FOR_SALE"
								}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.saleStatus").value("NOT_FOR_SALE"));
	}

	@Test
	@DisplayName("판매중으로 되돌리면 400 과 LISTING_400_STATUS 다")
	void changeSaleStatus_toOnSale_returnsBadRequest() throws Exception {
		willThrow(new CustomException(ListingErrorCode.LISTING_400_STATUS))
				.given(listingService).changeSaleStatus(anyLong(), anyLong(), any());

		mvc.perform(patch(ITEMS_URL + "/" + ITEM_ID + "/sale-status")
						.with(authentication(principalOf(USER_ID))).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "saleStatus": "ON_SALE"
								}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("LISTING_400_STATUS"));
	}

	@Test
	@DisplayName("판매 상태를 비워 보내면 400 이다")
	void changeSaleStatus_missingStatus_returnsBadRequest() throws Exception {
		mvc.perform(patch(ITEMS_URL + "/" + ITEM_ID + "/sale-status")
						.with(authentication(principalOf(USER_ID))).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	private static String createBody() {
		return """
				{
				  "item": {
				    "brandName": "Levis",
				    "name": "LVC 1955 501",
				    "categoryId": 116,
				    "sizeLabel": "L",
				    "wearTarget": "MALE",
				    "coverImageKey": "item/2026/09/a.jpg"
				  },
				  "price": 320000,
				  "featuredPostIds": [12]
				}""";
	}

	private static UsernamePasswordAuthenticationToken principalOf(Long userId) {
		return new UsernamePasswordAuthenticationToken(new AuthUser(userId), null, List.of());
	}
}
