package com.tenure.domain.post.controller;

import com.tenure.domain.post.dto.response.MyPostSummaryResponse;
import com.tenure.domain.post.dto.response.UserPostSummaryResponse;
import com.tenure.domain.post.enums.AspectRatio;
import com.tenure.domain.post.enums.MyPostTab;
import com.tenure.domain.post.service.PostQueryService;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.global.exception.CustomException;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorCodec;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.security.AuthUser;
import com.tenure.support.ControllerSliceTestSupport;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserPostController.class)
@Import(CursorCodec.class)
@DisplayName("UserPostController")
class UserPostControllerTest extends ControllerSliceTestSupport {

	private static final String MY_POSTS_URL = "/api/v1/users/me/posts";
	private static final Long USER_ID = 1L;
	private static final Long OTHER_USER_ID = 2L;
	private static final Long POST_ID = 10L;

	@MockitoBean private PostQueryService postQueryService;

	@Test
	@DisplayName("기본 탭은 게시물이고 월을 주지 않으면 전체다")
	void getMyPosts_noParams_usesDefaults() throws Exception {
		CursorSlice<MyPostSummaryResponse> slice = CursorSlice.of(
				List.of(myPost(), myPost()), 1,
				card -> Cursor.of(LocalDate.of(2026, 6, 21), POST_ID));
		given(postQueryService.getMyPosts(USER_ID, MyPostTab.POSTS, null, null, 20)).willReturn(slice);

		mvc.perform(get(MY_POSTS_URL).with(authentication(principalOf(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].id").value(POST_ID))
				.andExpect(jsonPath("$.data.content[0].wornDate").value("2026-06-21"))
				.andExpect(jsonPath("$.data.content[0].isLiked").value(true))
				.andExpect(jsonPath("$.data.nextCursor").isString());
	}

	@Test
	@DisplayName("yearMonth 는 2026-06 형식으로 받는다 — 월 헤더로 묶는 화면의 입력이다")
	void getMyPosts_yearMonthParam_bindsYearMonth() throws Exception {
		given(postQueryService.getMyPosts(
				USER_ID, MyPostTab.LIKED, YearMonth.of(2026, 6), null, 20))
				.willReturn(CursorSlice.empty());

		mvc.perform(get(MY_POSTS_URL)
						.param("tab", "LIKED")
						.param("yearMonth", "2026-06")
						.with(authentication(principalOf(USER_ID))))
				.andExpect(status().isOk());

		verify(postQueryService).getMyPosts(
				USER_ID, MyPostTab.LIKED, YearMonth.of(2026, 6), null, 20);
	}

	@Test
	@DisplayName("월 형식이 틀리면 400 이다")
	void getMyPosts_malformedYearMonth_returnsBadRequest() throws Exception {
		mvc.perform(get(MY_POSTS_URL)
						.param("yearMonth", "2026년 6월")
						.with(authentication(principalOf(USER_ID))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_400"));

		verifyNoInteractions(postQueryService);
	}

	@Test
	@DisplayName("size 가 상한을 넘으면 400 이다 — 한 번에 전체를 긁어가지 못한다")
	void getMyPosts_hugeSize_returnsBadRequest() throws Exception {
		mvc.perform(get(MY_POSTS_URL).param("size", "100000")
						.with(authentication(principalOf(USER_ID))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_400"));

		verifyNoInteractions(postQueryService);
	}

	@Test
	@DisplayName("size 가 0 이하여도 400 이다")
	void getMyPosts_zeroSize_returnsBadRequest() throws Exception {
		mvc.perform(get(MY_POSTS_URL).param("size", "0")
						.with(authentication(principalOf(USER_ID))))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(postQueryService);
	}

	@Test
	@DisplayName("타인 게시물에도 좋아요·저장 여부를 싣는다 — 그리드 카드에 오버레이 아이콘이 있다")
	void getUserPosts_otherUser_hasReactionFlags() throws Exception {
		given(postQueryService.getUserPosts(USER_ID, OTHER_USER_ID, null, 20))
				.willReturn(CursorSlice.of(List.of(userPost()), 20, card -> null));

		mvc.perform(get("/api/v1/users/" + OTHER_USER_ID + "/posts")
						.with(authentication(principalOf(USER_ID))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].id").value(POST_ID))
				.andExpect(jsonPath("$.data.content[0].aspectRatio").value("RATIO_4_5"))
				.andExpect(jsonPath("$.data.content[0].isLiked").value(false))
				.andExpect(jsonPath("$.data.content[0].isSaved").value(true));
	}

	@Test
	@DisplayName("없는 사용자면 404 와 USER_404 다")
	void getUserPosts_unknownUser_returnsNotFound() throws Exception {
		willThrow(new CustomException(UserErrorCode.USER_404))
				.given(postQueryService).getUserPosts(USER_ID, OTHER_USER_ID, null, 20);

		mvc.perform(get("/api/v1/users/" + OTHER_USER_ID + "/posts")
						.with(authentication(principalOf(USER_ID))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_404"));
	}

	@Test
	@DisplayName("토큰이 없으면 401 이다")
	void getMyPosts_noAuth_returnsUnauthorized() throws Exception {
		mvc.perform(get(MY_POSTS_URL))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(postQueryService);
	}

	private static UsernamePasswordAuthenticationToken principalOf(Long userId) {
		return new UsernamePasswordAuthenticationToken(new AuthUser(userId), null, List.of());
	}

	private static MyPostSummaryResponse myPost() {
		return MyPostSummaryResponse.builder()
				.id(POST_ID)
				.imageUrl("/uploads/post/2026/09/a.jpg")
				.aspectRatio(AspectRatio.RATIO_3_4)
				.wornDate(LocalDate.of(2026, 6, 21))
				.isLiked(true)
				.isSaved(false)
				.build();
	}

	private static UserPostSummaryResponse userPost() {
		return UserPostSummaryResponse.builder()
				.id(POST_ID)
				.imageUrl("/uploads/post/2026/09/a.jpg")
				.aspectRatio(AspectRatio.RATIO_4_5)
				.wornDate(LocalDate.of(2026, 6, 21))
				.isLiked(false)
				.isSaved(true)
				.build();
	}
}
