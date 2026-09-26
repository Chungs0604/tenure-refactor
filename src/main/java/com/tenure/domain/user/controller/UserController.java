package com.tenure.domain.user.controller;

import com.tenure.domain.user.dto.request.UserCreateRequest;
import com.tenure.domain.user.dto.response.AvailabilityResponse;
import com.tenure.domain.user.dto.response.UserCreateResponse;
import com.tenure.domain.user.service.UserService;
import com.tenure.global.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
