package com.tenure.domain.user.controller;

import com.tenure.domain.user.dto.request.UserCreateRequest;
import com.tenure.domain.user.dto.request.UserUpdateRequest;
import com.tenure.domain.user.dto.request.UserWithdrawRequest;
import com.tenure.domain.user.dto.response.AccountResponse;
import com.tenure.domain.user.dto.response.AvailabilityResponse;
import com.tenure.domain.user.dto.response.MyProfileResponse;
import com.tenure.domain.user.dto.response.UserCreateResponse;
import com.tenure.domain.user.dto.response.UserProfileResponse;
import com.tenure.domain.user.dto.response.UserUpdateResponse;
import com.tenure.domain.user.service.UserService;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController implements UserApi {

	private final UserService userService;

	@Override
	@GetMapping("/check-email")
	public ResponseEntity<BaseResponse<AvailabilityResponse>> checkEmail(
			@RequestParam String email
	) {
		AvailabilityResponse response = userService.checkEmail(email);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	@Override
	@GetMapping("/check-nickname")
	public ResponseEntity<BaseResponse<AvailabilityResponse>> checkNickname(
			@RequestParam String nickname
	) {
		AvailabilityResponse response = userService.checkNickname(nickname);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	@Override
	@PostMapping
	public ResponseEntity<BaseResponse<UserCreateResponse>> signUp(
			@RequestBody UserCreateRequest request
	) {
		UserCreateResponse response = userService.signUp(request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 계정 정보 조회
	@Override
	@GetMapping("/me/account")
	public ResponseEntity<BaseResponse<AccountResponse>> getMyAccount(
			@AuthenticationPrincipal AuthUser authUser
	) {
		AccountResponse response = userService.getMyAccount(authUser.id());
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 내 프로필 조회
	@Override
	@GetMapping("/me")
	public ResponseEntity<BaseResponse<MyProfileResponse>> getMyProfile(
			@AuthenticationPrincipal AuthUser authUser
	) {
		MyProfileResponse response = userService.getMyProfile(authUser.id());
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 프로필 수정
	@Override
	@PatchMapping("/me")
	public ResponseEntity<BaseResponse<UserUpdateResponse>> updateProfile(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestBody UserUpdateRequest request
	) {
		UserUpdateResponse response = userService.updateProfile(authUser.id(), request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 타인 프로필 조회
	@Override
	@GetMapping("/{userId}")
	public ResponseEntity<BaseResponse<UserProfileResponse>> getUserProfile(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long userId
	) {
		UserProfileResponse response = userService.getUserProfile(authUser.id(), userId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 회원탈퇴
	@Override
	@DeleteMapping("/me")
	public ResponseEntity<BaseResponse<Void>> withdraw(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestBody UserWithdrawRequest request
	) {
		userService.withdraw(authUser.id(), request);
		return ResponseEntity.ok(BaseResponse.success());
	}
}
