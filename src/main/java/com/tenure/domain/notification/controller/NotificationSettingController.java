package com.tenure.domain.notification.controller;

import com.tenure.domain.notification.dto.request.NotificationSettingUpdateRequest;
import com.tenure.domain.notification.dto.response.NotificationSettingResponse;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification-settings")
@RequiredArgsConstructor
public class NotificationSettingController implements NotificationSettingApi {

	private final NotificationService notificationService;

	// 알림 수신 설정 조회
	@Override
	@GetMapping
	public ResponseEntity<BaseResponse<List<NotificationSettingResponse>>> getSettings(
			@AuthenticationPrincipal AuthUser authUser
	) {
		List<NotificationSettingResponse> response = notificationService.getSettings(authUser.id());
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 알림 수신 설정 변경
	@Override
	@PatchMapping
	public ResponseEntity<BaseResponse<Void>> updateSettings(
			@AuthenticationPrincipal AuthUser authUser,
			@Valid @RequestBody NotificationSettingUpdateRequest request
	) {
		notificationService.updateSettings(authUser.id(), request);
		return ResponseEntity.ok(BaseResponse.success());
	}
}
