package com.tenure.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tenure.domain.notification.dto.request.NotificationSettingUpdateRequest;
import com.tenure.domain.notification.dto.response.NotificationSettingResponse;
import com.tenure.domain.notification.enums.NotificationSettingType;
import com.tenure.domain.notification.exception.NotificationErrorCode;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.global.exception.CustomException;
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

@WebMvcTest(NotificationSettingController.class)
@DisplayName("NotificationSettingController")
class NotificationSettingControllerTest extends ControllerSliceTestSupport {

	private static final String URL = "/api/v1/notification-settings";
	private static final Long USER_ID = 1L;

	@MockitoBean private NotificationService notificationService;

	@Nested
	@DisplayName("GET /api/v1/notification-settings")
	class GetSettings {

		@Test
		@DisplayName("조회에 성공하면 설정 목록을 내려준다")
		void getSettings_valid_returnsSettings() throws Exception {
			given(notificationService.getSettings(USER_ID)).willReturn(List.of(
					NotificationSettingResponse.builder()
							.settingType(NotificationSettingType.FOLLOW)
							.group("활동 알림")
							.title("팔로우 알림")
							.description("누군가 나를 팔로우하면 알림을 받아요.")
							.enabled(false)
							.build()));

			mvc.perform(get(URL).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].settingType").value("FOLLOW"))
					.andExpect(jsonPath("$.data[0].enabled").value(false));
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void getSettings_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(get(URL))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(notificationService);
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/notification-settings")
	class UpdateSettings {

		@Test
		@DisplayName("변경에 성공하면 200 이다")
		void updateSettings_valid_returnsOk() throws Exception {
			mvc.perform(patch(URL)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(request(NotificationSettingType.FOLLOW, true))))
					.andExpect(status().isOk());

			verify(notificationService).updateSettings(eq(USER_ID), any());
		}

		@Test
		@DisplayName("필수 그룹을 끄려 하면 400 이다")
		void updateSettings_requiredGroupOff_returnsBadRequest() throws Exception {
			willThrow(new CustomException(NotificationErrorCode.NOTIFICATION_400_REQUIRED))
					.given(notificationService).updateSettings(eq(USER_ID), any());

			mvc.perform(patch(URL)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(request(NotificationSettingType.TRADE_REQUEST, false))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("NOTIFICATION_400_REQUIRED"));
		}

		@Test
		@DisplayName("settings 가 비어 있으면 400 이다")
		void updateSettings_emptySettings_returnsBadRequest() throws Exception {
			mvc.perform(patch(URL)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(NotificationSettingUpdateRequest.builder().settings(List.of()).build())))
					.andExpect(status().isBadRequest());

			verifyNoInteractions(notificationService);
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void updateSettings_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(patch(URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(request(NotificationSettingType.FOLLOW, true))))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(notificationService);
		}
	}

	private static NotificationSettingUpdateRequest request(NotificationSettingType type, boolean enabled) {
		return NotificationSettingUpdateRequest.builder()
				.settings(List.of(NotificationSettingUpdateRequest.SettingItem.builder()
						.settingType(type).enabled(enabled).build()))
				.build();
	}

	private static UsernamePasswordAuthenticationToken principalOf(Long userId) {
		return new UsernamePasswordAuthenticationToken(new AuthUser(userId), null, List.of());
	}
}
