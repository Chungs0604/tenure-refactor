package com.tenure.domain.auth.controller;

import com.tenure.domain.auth.dto.request.EmailVerificationConfirmRequest;
import com.tenure.domain.auth.dto.request.EmailVerificationSendRequest;
import com.tenure.domain.auth.dto.request.LoginRequest;
import com.tenure.domain.auth.dto.response.EmailVerificationConfirmResponse;
import com.tenure.domain.auth.dto.response.EmailVerificationSendResponse;
import com.tenure.domain.auth.dto.response.LoginResponse;
import com.tenure.domain.auth.service.AuthService;
import com.tenure.global.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

	private final AuthService authService;

	@Override
	@PostMapping("/login")
	public ResponseEntity<BaseResponse<LoginResponse>> login(
			@RequestBody LoginRequest request
	) {
		LoginResponse response = authService.login(request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	@Override
	@PostMapping("/email-verifications")
	public ResponseEntity<BaseResponse<EmailVerificationSendResponse>> sendCode(
			@RequestBody EmailVerificationSendRequest request
	) {
		EmailVerificationSendResponse response = authService.sendCode(request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	@Override
	@PostMapping("/email-verifications/verify")
	public ResponseEntity<BaseResponse<EmailVerificationConfirmResponse>> confirmCode(
			@RequestBody EmailVerificationConfirmRequest request
	) {
		EmailVerificationConfirmResponse response = authService.confirmCode(request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}
}
