package com.tenure.domain.notification.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tenure.domain.notification.dto.response.NotificationReadAllResponse;
import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.domain.notification.exception.NotificationErrorCode;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.domain.notification.dto.response.UnreadCountResponse;
import com.tenure.global.exception.CustomException;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorCodec;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.security.AuthUser;
import com.tenure.support.ControllerSliceTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.BDDMockito;

@WebMvcTest(NotificationController.class)
@Import(CursorCodec.class)
@DisplayName("NotificationController")
class NotificationControllerTest extends ControllerSliceTestSupport {

	private static final String URL = "/api/v1/notifications";
	private static final Long USER_ID = 1L;
	private static final Long NOTIFICATION_ID = 10L;

	@MockitoBean private NotificationService notificationService;

	@Nested
	@DisplayName("GET /api/v1/notifications")
	class GetNotifications {

		@Test
		@DisplayName("기본값은 전체 탭·size 20 이다")
		void getNotifications_noParams_usesDefaults() throws Exception {
			given(notificationService.list(USER_ID, null, null, 20)).willReturn(CursorSlice.empty());

			mvc.perform(get(URL).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data.hasNext").value(false));

			verify(notificationService).list(USER_ID, null, null, 20);
		}

		@Test
		@DisplayName("탭·커서·size 를 그대로 서비스에 넘긴다")
		void getNotifications_withParams_passesThroughToService() throws Exception {
			Cursor cursor = Cursor.of(10L);
			String encoded = new CursorCodec().encode(cursor);
			given(notificationService.list(USER_ID, NotificationCategory.WISHLIST, cursor, 5))
					.willReturn(CursorSlice.empty());

			mvc.perform(get(URL)
							.param("category", "WISHLIST")
							.param("cursor", encoded)
							.param("size", "5")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk());

			verify(notificationService).list(USER_ID, NotificationCategory.WISHLIST, cursor, 5);
		}

		@Test
		@DisplayName("깨진 커서는 400 이다")
		void getNotifications_malformedCursor_returnsBadRequest() throws Exception {
			mvc.perform(get(URL).param("cursor", "!!broken!!").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON_400"));

			verifyNoInteractions(notificationService);
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void getNotifications_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(get(URL))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("COMMON_401"));

			verifyNoInteractions(notificationService);
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/notifications/{notificationId}/read")
	class Read {

		@Test
		@DisplayName("읽음 처리에 성공하면 200 이다")
		void read_valid_returnsOk() throws Exception {
			mvc.perform(patch(URL + "/" + NOTIFICATION_ID + "/read").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk());

			verify(notificationService).read(USER_ID, NOTIFICATION_ID);
		}

		@Test
		@DisplayName("남의 알림이면 403 이다")
		void read_othersNotification_returnsForbidden() throws Exception {
			BDDMockito.willThrow(new CustomException(NotificationErrorCode.NOTIFICATION_403))
					.given(notificationService).read(USER_ID, NOTIFICATION_ID);

			mvc.perform(patch(URL + "/" + NOTIFICATION_ID + "/read").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("NOTIFICATION_403"));
		}

		@Test
		@DisplayName("없는 알림이면 404 다")
		void read_unknownNotification_returnsNotFound() throws Exception {
			BDDMockito.willThrow(new CustomException(NotificationErrorCode.NOTIFICATION_404))
					.given(notificationService).read(USER_ID, NOTIFICATION_ID);

			mvc.perform(patch(URL + "/" + NOTIFICATION_ID + "/read").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("NOTIFICATION_404"));
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void read_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(patch(URL + "/" + NOTIFICATION_ID + "/read"))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(notificationService);
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/notifications/read-all")
	class ReadAll {

		@Test
		@DisplayName("category 없이 호출하면 전체를 읽음 처리한다")
		void readAll_noCategory_passesNullCategory() throws Exception {
			given(notificationService.readAll(USER_ID, null))
					.willReturn(NotificationReadAllResponse.builder().updatedCount(3).build());

			mvc.perform(patch(URL + "/read-all").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.updatedCount").value(3));

			verify(notificationService).readAll(USER_ID, null);
		}

		@Test
		@DisplayName("category 를 주면 그 탭만 읽음 처리한다")
		void readAll_withCategory_passesCategory() throws Exception {
			given(notificationService.readAll(USER_ID, NotificationCategory.TRADE_STATUS))
					.willReturn(NotificationReadAllResponse.builder().updatedCount(1).build());

			mvc.perform(patch(URL + "/read-all")
							.param("category", "TRADE_STATUS")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.updatedCount").value(1));

			verify(notificationService).readAll(USER_ID, NotificationCategory.TRADE_STATUS);
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void readAll_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(patch(URL + "/read-all"))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(notificationService);
		}
	}

	@Nested
	@DisplayName("GET /api/v1/notifications/unread-count")
	class GetUnreadCount {

		@Test
		@DisplayName("조회에 성공하면 안읽음 수를 내려준다")
		void getUnreadCount_valid_returnsCount() throws Exception {
			given(notificationService.getUnreadCount(USER_ID))
					.willReturn(UnreadCountResponse.builder().unreadCount(4).build());

			mvc.perform(get(URL + "/unread-count").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.unreadCount").value(4));
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void getUnreadCount_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(get(URL + "/unread-count"))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(notificationService);
		}
	}

	private static UsernamePasswordAuthenticationToken principalOf(Long userId) {
		return new UsernamePasswordAuthenticationToken(new AuthUser(userId), null, List.of());
	}
}
