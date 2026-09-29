package com.tenure.domain.post.controller;

import com.tenure.domain.post.dto.response.TaggedItemResponse;
import com.tenure.domain.post.enums.TaggedItemStatus;
import com.tenure.domain.post.exception.PostErrorCode;
import com.tenure.domain.post.service.PostQueryService;
import com.tenure.global.exception.CustomException;
import com.tenure.global.security.AuthUser;
import com.tenure.support.ControllerSliceTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostImageController.class)
@DisplayName("PostImageController")
class PostImageControllerTest extends ControllerSliceTestSupport {

	private static final Long USER_ID = 1L;
	private static final Long IMAGE_ID = 500L;
	private static final String TAGGED_ITEMS_URL = "/api/v1/post-images/" + IMAGE_ID + "/tagged-items";

	@MockitoBean private PostQueryService postQueryService;

	@Test
	@DisplayName("태그된 아이템을 배열로 내려준다. 금액은 문자열이다")
	void getTaggedItems_hasTags_returnsArray() throws Exception {
		given(postQueryService.getTaggedItems(USER_ID, IMAGE_ID)).willReturn(List.of(
				onSale(), deleted()));

		mvc.perform(get(TAGGED_ITEMS_URL).with(authentication(principalOf(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2))
				.andExpect(jsonPath("$.data[0].status").value("ON_SALE"))
				.andExpect(jsonPath("$.data[0].price").value("360000"))
				.andExpect(jsonPath("$.data[0].alarmEnabled").value(true))
				// 삭제된 아이템은 회색 처리 대상이고 판매 등록이 없어 CTA 가 없다
				.andExpect(jsonPath("$.data[1].status").value("DELETED"))
				.andExpect(jsonPath("$.data[1].listingId").doesNotExist());
	}

	@Test
	@DisplayName("태그가 없으면 빈 배열이다")
	void getTaggedItems_noTags_returnsEmptyArray() throws Exception {
		given(postQueryService.getTaggedItems(USER_ID, IMAGE_ID)).willReturn(List.of());

		mvc.perform(get(TAGGED_ITEMS_URL).with(authentication(principalOf(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	@DisplayName("없는 사진이면 404 와 POST_404_IMAGE 다")
	void getTaggedItems_unknownImage_returnsNotFound() throws Exception {
		willThrow(new CustomException(PostErrorCode.POST_404_IMAGE))
				.given(postQueryService).getTaggedItems(USER_ID, IMAGE_ID);

		mvc.perform(get(TAGGED_ITEMS_URL).with(authentication(principalOf(USER_ID))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("POST_404_IMAGE"));
	}

	@Test
	@DisplayName("토큰이 없으면 401 이다")
	void getTaggedItems_noAuth_returnsUnauthorized() throws Exception {
		mvc.perform(get(TAGGED_ITEMS_URL))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(postQueryService);
	}

	private static UsernamePasswordAuthenticationToken principalOf(Long userId) {
		return new UsernamePasswordAuthenticationToken(new AuthUser(userId), null, List.of());
	}

	private static TaggedItemResponse onSale() {
		return TaggedItemResponse.builder()
				.itemId(100L)
				.listingId(300L)
				.brandName("Levis")
				.name("LVC 1955 501")
				.coverImageUrl("/uploads/item/2026/09/a.jpg")
				.status(TaggedItemStatus.ON_SALE)
				.price("360000")
				.offerAllowed(true)
				.alarmEnabled(true)
				.build();
	}

	private static TaggedItemResponse deleted() {
		return TaggedItemResponse.builder()
				.itemId(200L)
				.listingId(null)
				.brandName("Our Legacy")
				.name("Third Cut")
				.coverImageUrl("/uploads/item/2026/09/b.jpg")
				.status(TaggedItemStatus.DELETED)
				.price(null)
				.offerAllowed(false)
				.alarmEnabled(false)
				.build();
	}
}
