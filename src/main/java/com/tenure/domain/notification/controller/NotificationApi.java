package com.tenure.domain.notification.controller;

import com.tenure.domain.notification.dto.response.NotificationReadAllResponse;
import com.tenure.domain.notification.dto.response.NotificationResponse;
import com.tenure.domain.notification.dto.response.UnreadCountResponse;
import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.global.pagination.CursorResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
@Tag(name = "알림", description = "알림 목록·읽음 처리·안읽음 수 API")
public interface NotificationApi {

	@Operation(summary = "알림 목록", description = "탭(전체/확인 필요/아이템 소식/거래 현황/관심 등록). 최신순, 날짜 헤더는 클라이언트가 createdAt 으로 묶는다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공")
	})
	ResponseEntity<BaseResponse<CursorResponse<NotificationResponse>>> getNotifications(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "탭. 미지정 시 전체") NotificationCategory category,
			@Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 비운다") String cursor,
			@Parameter(description = "페이지 크기") @Min(1) @Max(100) int size);

	@Operation(summary = "알림 읽음", description = "본인 알림만 처리할 수 있다. 이미 읽은 알림도 에러 없이 멱등하게 끝난다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "처리 성공"),
			@ApiResponse(responseCode = "403", description = "NOTIFICATION_403"),
			@ApiResponse(responseCode = "404", description = "NOTIFICATION_404")
	})
	ResponseEntity<BaseResponse<Void>> read(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "알림 id") Long notificationId);

	@Operation(summary = "알림 전체 읽음", description = "category 를 주면 그 탭만 처리한다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "처리 성공")
	})
	ResponseEntity<BaseResponse<NotificationReadAllResponse>> readAll(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "특정 탭만 읽음 처리. 미지정 시 전체") NotificationCategory category);

	@Operation(summary = "안읽은 알림 수", description = "홈·마이페이지 헤더의 벨 아이콘 배지에 쓴다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공")
	})
	ResponseEntity<BaseResponse<UnreadCountResponse>> getUnreadCount(
			@Parameter(hidden = true) AuthUser authUser);
}
