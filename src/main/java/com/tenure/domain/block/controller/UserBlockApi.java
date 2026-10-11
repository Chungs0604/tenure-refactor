package com.tenure.domain.block.controller;

import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/** 사용자 경로(`/api/v1/users/{userId}`)를 쓰는 차단·차단해제 API. */
@Validated
@Tag(name = "차단", description = "사용자 차단·차단해제 API")
public interface UserBlockApi {

	@Operation(summary = "차단", description = "차단하면 상대가 새 거래 의사·구매 제안을 보낼 수 없고 채팅도 막힌다. 기존 채팅방은 남는다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "차단 성공"),
			@ApiResponse(responseCode = "404", description = "USER_404"),
			@ApiResponse(responseCode = "409", description = "BLOCK_409 — 이미 차단한 사용자"),
			@ApiResponse(responseCode = "400", description = "BLOCK_400_SELF — 자기 자신을 차단하려 함")
	})
	ResponseEntity<BaseResponse<Void>> block(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "차단할 사용자 id", example = "1") Long userId);

	@Operation(summary = "차단해제", description = "하드 삭제. 차단하지 않은 관계면 조용히 성공 처리한다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "차단해제 성공"),
			@ApiResponse(responseCode = "404", description = "USER_404")
	})
	ResponseEntity<BaseResponse<Void>> unblock(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "차단해제할 사용자 id", example = "1") Long userId);
}
