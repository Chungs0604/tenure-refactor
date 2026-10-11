package com.tenure.domain.block.controller;

import com.tenure.domain.block.service.BlockService;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/{userId}")
@RequiredArgsConstructor
public class UserBlockController implements UserBlockApi {

	private final BlockService blockService;

	// 차단
	@Override
	@PostMapping("/block")
	public ResponseEntity<BaseResponse<Void>> block(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long userId
	) {
		blockService.block(authUser.id(), userId);
		return ResponseEntity.ok(BaseResponse.success());
	}

	// 차단해제
	@Override
	@DeleteMapping("/block")
	public ResponseEntity<BaseResponse<Void>> unblock(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long userId
	) {
		blockService.unblock(authUser.id(), userId);
		return ResponseEntity.ok(BaseResponse.success());
	}
}
