package com.tenure.domain.report.controller;

import com.tenure.domain.report.dto.request.ReportRequest;
import com.tenure.domain.report.dto.response.ReportResponse;
import com.tenure.domain.report.service.ReportService;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController implements ReportApi {

	private final ReportService reportService;

	// 신고 접수
	@Override
	@PostMapping
	public ResponseEntity<BaseResponse<ReportResponse>> submit(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestBody ReportRequest request
	) {
		ReportResponse response = reportService.submit(authUser.id(), request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}
}
