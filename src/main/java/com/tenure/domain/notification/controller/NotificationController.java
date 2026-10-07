package com.tenure.domain.notification.controller;

import com.tenure.domain.notification.dto.response.NotificationReadAllResponse;
import com.tenure.domain.notification.dto.response.NotificationResponse;
import com.tenure.domain.notification.dto.response.UnreadCountResponse;
import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.global.pagination.CursorCodec;
import com.tenure.global.pagination.CursorResponse;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

	private final NotificationService notificationService;
	private final CursorCodec cursorCodec;

	// 알림 목록
	@Override
	@GetMapping
	public ResponseEntity<BaseResponse<CursorResponse<NotificationResponse>>> getNotifications(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(required = false) NotificationCategory category,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		CursorSlice<NotificationResponse> slice =
				notificationService.list(authUser.id(), category, cursorCodec.decode(cursor), size);
		return ResponseEntity.ok(BaseResponse.success(cursorCodec.toResponse(slice)));
	}

	// 알림 읽음
	@Override
	@PatchMapping("/{notificationId}/read")
	public ResponseEntity<BaseResponse<Void>> read(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long notificationId
	) {
		notificationService.read(authUser.id(), notificationId);
		return ResponseEntity.ok(BaseResponse.success());
	}

	// 전체 읽음
	@Override
	@PatchMapping("/read-all")
	public ResponseEntity<BaseResponse<NotificationReadAllResponse>> readAll(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(required = false) NotificationCategory category
	) {
		NotificationReadAllResponse response = notificationService.readAll(authUser.id(), category);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 안읽은 알림 수
	@Override
	@GetMapping("/unread-count")
	public ResponseEntity<BaseResponse<UnreadCountResponse>> getUnreadCount(
			@AuthenticationPrincipal AuthUser authUser
	) {
		UnreadCountResponse response = notificationService.getUnreadCount(authUser.id());
		return ResponseEntity.ok(BaseResponse.success(response));
	}
}
