package com.tenure.domain.payout.controller;

import com.tenure.domain.payout.dto.request.PayoutAccountCreateRequest;
import com.tenure.domain.payout.dto.request.PayoutAccountUpdateRequest;
import com.tenure.domain.payout.dto.response.BankResponse;
import com.tenure.domain.payout.dto.response.PayoutAccountIdResponse;
import com.tenure.domain.payout.dto.response.PayoutAccountResponse;
import com.tenure.domain.payout.service.PayoutService;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payout-accounts")
@RequiredArgsConstructor
public class PayoutController implements PayoutApi {

	private final PayoutService payoutService;

	// 정산 계좌 목록
	@Override
	@GetMapping
	public ResponseEntity<BaseResponse<List<PayoutAccountResponse>>> getAccounts(
			@AuthenticationPrincipal AuthUser authUser
	) {
		List<PayoutAccountResponse> response = payoutService.getAccounts(authUser.id());
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 은행 목록 — 추가·수정 화면의 드롭다운 선택지
	@Override
	@GetMapping("/banks")
	public ResponseEntity<BaseResponse<List<BankResponse>>> getBanks(
			@AuthenticationPrincipal AuthUser authUser
	) {
		List<BankResponse> response = payoutService.getBanks();
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 정산 계좌 등록
	@Override
	@PostMapping
	public ResponseEntity<BaseResponse<PayoutAccountIdResponse>> create(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestBody PayoutAccountCreateRequest request
	) {
		PayoutAccountIdResponse response = payoutService.create(authUser.id(), request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 정산 계좌 수정
	@Override
	@PutMapping("/{accountId}")
	public ResponseEntity<BaseResponse<PayoutAccountIdResponse>> update(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long accountId,
			@RequestBody PayoutAccountUpdateRequest request
	) {
		PayoutAccountIdResponse response = payoutService.update(authUser.id(), accountId, request);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 정산 계좌 삭제
	@Override
	@DeleteMapping("/{accountId}")
	public ResponseEntity<BaseResponse<Void>> delete(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long accountId
	) {
		payoutService.delete(authUser.id(), accountId);
		return ResponseEntity.ok(BaseResponse.success());
	}

	// 기본 정산 계좌 지정
	@Override
	@PatchMapping("/{accountId}/default")
	public ResponseEntity<BaseResponse<Void>> changeDefault(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long accountId
	) {
		payoutService.changeDefault(authUser.id(), accountId);
		return ResponseEntity.ok(BaseResponse.success());
	}
}
