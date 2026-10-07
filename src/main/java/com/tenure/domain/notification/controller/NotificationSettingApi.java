package com.tenure.domain.notification.controller;

import com.tenure.domain.notification.dto.request.NotificationSettingUpdateRequest;
import com.tenure.domain.notification.dto.response.NotificationSettingResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "알림 설정", description = "알림 수신 설정 조회·변경 API")
public interface NotificationSettingApi {

	@Operation(summary = "알림 수신 설정 조회", description = "9종. 한 번도 안 바꾼 유형은 기본값으로 채워 내려간다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공")
	})
	ResponseEntity<BaseResponse<List<NotificationSettingResponse>>> getSettings(
			@Parameter(hidden = true) AuthUser authUser);

	@Operation(summary = "알림 수신 설정 변경", description = "부분 갱신. 필수 그룹(거래 요청 등 4종)은 끌 수 없다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "변경 성공"),
			@ApiResponse(responseCode = "400", description = "NOTIFICATION_400_REQUIRED / COMMON_400")
	})
	ResponseEntity<BaseResponse<Void>> updateSettings(
			@Parameter(hidden = true) AuthUser authUser,
			@Valid NotificationSettingUpdateRequest request);
}
