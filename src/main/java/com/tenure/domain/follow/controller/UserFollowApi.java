package com.tenure.domain.follow.controller;

import com.tenure.domain.user.dto.response.UserSummaryResponse;
import com.tenure.global.pagination.CursorResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/** 사용자 경로(`/api/v1/users/{userId}`)를 쓰는 팔로워/팔로잉 목록, 팔로우·언팔로우 API. */
@Validated
@Tag(name = "팔로우", description = "팔로워·팔로잉 목록, 팔로우·언팔로우 API")
public interface UserFollowApi {

	@Operation(summary = "팔로워 목록",
			description = "userId 를 팔로우하는 사용자 목록. 최신 팔로우 순. 차단 관계면 빈 목록을 돌려준다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "404", description = "USER_404")
	})
	ResponseEntity<BaseResponse<CursorResponse<UserSummaryResponse>>> getFollowers(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "사용자 id", example = "1") Long userId,
			@Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 비운다") String cursor,
			@Parameter(description = "페이지 크기") @Min(1) @Max(100) int size);

	@Operation(summary = "팔로잉 목록",
			description = "userId 가 팔로우하는 사용자 목록. API-050 과 동일한 스키마, 방향만 다르다. 차단 관계면 빈 목록을 돌려준다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "404", description = "USER_404")
	})
	ResponseEntity<BaseResponse<CursorResponse<UserSummaryResponse>>> getFollowings(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "사용자 id", example = "1") Long userId,
			@Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 비운다") String cursor,
			@Parameter(description = "페이지 크기") @Min(1) @Max(100) int size);

	@Operation(summary = "팔로우",
			description = "멱등하지 않다. follow 생성과 같은 트랜잭션에서 대상의 follower_count 를 +1 한다. 차단 관계면 거절한다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "팔로우 성공"),
			@ApiResponse(responseCode = "404", description = "USER_404"),
			@ApiResponse(responseCode = "409", description = "FOLLOW_409 — 이미 팔로우한 사용자"),
			@ApiResponse(responseCode = "400", description = "FOLLOW_400_SELF — 자기 자신을 팔로우하려 함"),
			@ApiResponse(responseCode = "403", description = "FOLLOW_403_BLOCKED — 차단 관계")
	})
	ResponseEntity<BaseResponse<Void>> follow(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "팔로우할 사용자 id", example = "1") Long userId);

	@Operation(summary = "언팔로우",
			description = "follower_count 를 -1 한다. 없는 관계면 404 대신 조용히 성공 처리한다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "언팔로우 성공"),
			@ApiResponse(responseCode = "404", description = "USER_404")
	})
	ResponseEntity<BaseResponse<Void>> unfollow(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "언팔로우할 사용자 id", example = "1") Long userId);
}
