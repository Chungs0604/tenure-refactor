package com.tenure.domain.item.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tenure.domain.item.dto.response.ItemIdResponse;
import com.tenure.domain.item.dto.response.ItemListingSummaryResponse;
import com.tenure.domain.item.dto.response.ItemSummaryResponse;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.service.ItemQueryService;
import com.tenure.domain.item.service.ItemService;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.global.exception.CustomException;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorCodec;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.security.AuthUser;
import com.tenure.support.ControllerSliceTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(ItemController.class)
@Import(CursorCodec.class)
@DisplayName("ItemController")
class ItemControllerTest extends ControllerSliceTestSupport {

	private static final Long USER_ID = 1L;
	private static final Long ITEM_ID = 100L;
	private static final String ITEMS_URL = "/api/v1/items";

	@MockitoBean private ItemService itemService;
	@MockitoBean private ItemQueryService itemQueryService;

	@Test
	@DisplayName("등록에 성공하면 아이템 id 를 내려준다")
	void create_success_returnsId() throws Exception {
		given(itemService.create(anyLong(), any())).willReturn(ItemIdResponse.builder().id(ITEM_ID).build());

		mvc.perform(post(ITEMS_URL).with(authentication(principalOf(USER_ID))).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "LVC 1955 501",
								  "brandName": "Levis",
								  "categoryId": 116,
								  "sizeLabel": "L",
								  "wearTarget": "MALE",
								  "coverImageKey": "item/2026/09/a.jpg"
								}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(ITEM_ID));
	}

	@Test
	@DisplayName("카테고리에 없는 사이즈면 400 과 ITEM_400_SIZE 다")
	void create_invalidSize_returnsBadRequest() throws Exception {
		willThrow(new CustomException(ItemErrorCode.ITEM_400_SIZE))
				.given(itemService).create(anyLong(), any());

		mvc.perform(post(ITEMS_URL).with(authentication(principalOf(USER_ID))).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "LVC 1955 501",
								  "brandName": "Levis",
								  "categoryId": 116,
								  "sizeLabel": "999XL",
								  "wearTarget": "MALE",
								  "coverImageKey": "item/2026/09/a.jpg"
								}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ITEM_400_SIZE"));
	}

	@Test
	@DisplayName("착용 대상은 필수다 — 등록 폼에 필수 표시가 있다")
	void create_missingWearTarget_returnsBadRequest() throws Exception {
		mvc.perform(post(ITEMS_URL).with(authentication(principalOf(USER_ID))).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "LVC 1955 501",
								  "brandName": "Levis",
								  "categoryId": 116,
								  "coverImageKey": "item/2026/09/a.jpg"
								}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("수정은 폼 전체를 받는다 — 브랜드명을 빠뜨리면 400 이다")
	void update_missingBrandName_returnsBadRequest() throws Exception {
		mvc.perform(put(ITEMS_URL + "/1").with(authentication(principalOf(USER_ID))).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "LVC 1955 501",
								  "categoryId": 116,
								  "sizeLabel": "L",
								  "wearTarget": "MALE"
								}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("구매 제안 허용은 폼 없이 토글만 보낸다 — 선택 모달의 항목이다")
	void changeOfferAllowed_togglesWithoutForm() throws Exception {
		given(itemService.changeOfferAllowed(anyLong(), anyLong(), anyBoolean()))
				.willReturn(ItemIdResponse.builder().id(1L).build());

		mvc.perform(patch(ITEMS_URL + "/1/offer-allowed")
						.with(authentication(principalOf(USER_ID))).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "offerAllowed": true
								}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(1));
	}

	@Test
	@DisplayName("목록은 판매 상태를 아이템 레벨로 내려주고 판매 등록이 없으면 listing 이 없다")
	void getMyItems_flattensSaleStatus() throws Exception {
		given(itemQueryService.getMyItems(anyLong(), any(), any(), anyInt()))
				.willReturn(slice(List.of(
						summary(1L, SaleStatus.NOT_FOR_SALE, null),
						summary(2L, SaleStatus.ON_SALE, "360000"))));

		mvc.perform(get(ITEMS_URL).with(authentication(principalOf(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(2))
				.andExpect(jsonPath("$.data.content[0].saleStatus").value("NOT_FOR_SALE"))
				.andExpect(jsonPath("$.data.content[0].listing").doesNotExist())
				.andExpect(jsonPath("$.data.content[0].lastWornDate").value("2026-05-26"))
				.andExpect(jsonPath("$.data.content[1].saleStatus").value("ON_SALE"))
				.andExpect(jsonPath("$.data.content[1].listing.price").value("360000"))
				.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@Test
	@DisplayName("남의 아이템 상세는 403 과 ITEM_403 이다")
	void getItem_notOwner_returnsForbidden() throws Exception {
		willThrow(new CustomException(ItemErrorCode.ITEM_403))
				.given(itemQueryService).getItem(USER_ID, ITEM_ID);

		mvc.perform(get(ITEMS_URL + "/" + ITEM_ID).with(authentication(principalOf(USER_ID))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ITEM_403"));
	}

	@Test
	@DisplayName("판매 중이면 삭제가 409 와 ITEM_409_ON_SALE 이다")
	void delete_onSale_returnsConflict() throws Exception {
		willThrow(new CustomException(ItemErrorCode.ITEM_409_ON_SALE))
				.given(itemService).delete(USER_ID, ITEM_ID);

		mvc.perform(delete(ITEMS_URL + "/" + ITEM_ID)
						.with(authentication(principalOf(USER_ID))).with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ITEM_409_ON_SALE"));
	}

	@Test
	@DisplayName("size 가 범위를 벗어나면 400 이다")
	void getMyItems_sizeOutOfRange_returnsBadRequest() throws Exception {
		mvc.perform(get(ITEMS_URL).param("size", "101").with(authentication(principalOf(USER_ID))))
				.andExpect(status().isBadRequest());
	}

	private static UsernamePasswordAuthenticationToken principalOf(Long userId) {
		return new UsernamePasswordAuthenticationToken(new AuthUser(userId), null, List.of());
	}

	private static CursorSlice<ItemSummaryResponse> slice(List<ItemSummaryResponse> rows) {
		return CursorSlice.of(rows, rows.size(), row -> Cursor.of(LocalDate.of(2026, 5, 26), row.getId()));
	}

	private static ItemSummaryResponse summary(Long id, SaleStatus saleStatus, String price) {
		return ItemSummaryResponse.builder()
				.id(id)
				.brandName("Levis")
				.name("LVC 1955 501")
				.coverImageUrl("/uploads/item/2026/09/a.jpg")
				.saleStatus(saleStatus)
				.lastWornDate(LocalDate.of(2026, 5, 26))
				.listing(price == null ? null
						: ItemListingSummaryResponse.builder().id(300L).price(price).build())
				.build();
	}
}
