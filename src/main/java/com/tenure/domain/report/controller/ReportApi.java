package com.tenure.domain.report.controller;

import com.tenure.domain.report.dto.request.ReportRequest;
import com.tenure.domain.report.dto.response.ReportResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
@Tag(name = "신고", description = "게시물·상품 신고 API")
public interface ReportApi {

	@Operation(summary = "신고 접수",
			description = "접수까지만 담는다. targetType 에 맞지 않는 reason 이거나 USER 신고면 COMMON_400.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "접수 성공"),
			@ApiResponse(responseCode = "400", description = "COMMON_400 — USER 신고 또는 targetType 에 맞지 않는 reason"),
			@ApiResponse(responseCode = "404", description = "REPORT_404_TARGET")
	})
	ResponseEntity<BaseResponse<ReportResponse>> submit(
			@Parameter(hidden = true) AuthUser authUser,
			@Valid ReportRequest request);
}
