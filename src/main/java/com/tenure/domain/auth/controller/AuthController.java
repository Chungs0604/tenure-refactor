package com.tenure.domain.auth.controller;

import com.tenure.domain.auth.dto.request.EmailVerificationConfirmRequest;
import com.tenure.domain.auth.dto.request.EmailVerificationSendRequest;
import com.tenure.domain.auth.dto.request.LoginRequest;
import com.tenure.domain.auth.dto.request.PasswordResetRequest;
import com.tenure.domain.auth.dto.request.TokenReissueRequest;
import com.tenure.domain.auth.dto.response.EmailVerificationConfirmResponse;
import com.tenure.domain.auth.dto.response.EmailVerificationSendResponse;
import com.tenure.domain.auth.dto.response.LoginResponse;
import com.tenure.domain.auth.dto.response.TokenReissueResponse;
import com.tenure.domain.auth.service.AuthService;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

	private final AuthService authService;

	// 로그인
	@Override
	@PostMapping("/login")
	public ResponseEntity<BaseResponse<LoginResponse>> login(
			@RequestBody LoginRequest request
	) {
		LoginResponse response = authService.login(request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 로그아웃
	@Override
	@PostMapping("/logout")
	public ResponseEntity<BaseResponse<Void>> logout(
			@AuthenticationPrincipal AuthUser authUser
	) {
		authService.logout(authUser.id());
		return ResponseEntity.ok(BaseResponse.success());
	}

	// 엑세스 토큰 재발급
	@Override
	@PostMapping("/reissue")
	public ResponseEntity<BaseResponse<TokenReissueResponse>> reissue(
			@RequestBody TokenReissueRequest request
	) {
		TokenReissueResponse response = authService.reissue(request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 인증번호 발송
	@Override
	@PostMapping("/email-verifications")
	public ResponseEntity<BaseResponse<EmailVerificationSendResponse>> sendCode(
			@RequestBody EmailVerificationSendRequest request
	) {
		EmailVerificationSendResponse response = authService.sendCode(request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 인증번호 확인
	@Override
	@PostMapping("/email-verifications/verify")
	public ResponseEntity<BaseResponse<EmailVerificationConfirmResponse>> confirmCode(
			@RequestBody EmailVerificationConfirmRequest request
	) {
		EmailVerificationConfirmResponse response = authService.confirmCode(request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 비밀번호 재설정
	@Override
	@PatchMapping("/password")
	public ResponseEntity<BaseResponse<Void>> resetPassword(
			@RequestBody PasswordResetRequest request
	) {
		authService.resetPassword(request);
		return ResponseEntity.ok(BaseResponse.success());
	}
}
