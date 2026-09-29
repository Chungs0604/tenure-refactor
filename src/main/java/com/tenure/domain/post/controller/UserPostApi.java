package com.tenure.domain.post.controller;

import com.tenure.domain.post.dto.response.MyPostSummaryResponse;
import com.tenure.domain.post.dto.response.UserPostSummaryResponse;
import com.tenure.domain.post.enums.MyPostTab;
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
import java.time.YearMonth;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/**
 * 사용자 경로에 걸리는 게시물 목록. 경로는 users 지만 게시물 도메인이 담당한다 —
 * 조회하는 것이 게시물이라 user 쪽에 두면 사용자 도메인이 게시물 서비스를 끌어들인다.
 */
@Validated
@Tag(name = "게시물", description = "OOTD 게시물 API")
public interface UserPostApi {

	@Operation(summary = "내 게시물 (게시물 / 좋아요 / 저장 탭)",
			description = "마이페이지의 세 탭을 한 엔드포인트가 담는다 — 그리드 구성이 같고 탭만 "
					+ "'누구의 반응인가' 로 갈린다. yearMonth 를 주면 그 달만 본다. "
					+ "정렬은 착용일 최신순 고정이며 커서 키도 착용일이다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "400", description = "COMMON_400 — 커서·월 형식 오류 또는 size 범위 초과")
	})
	ResponseEntity<BaseResponse<CursorResponse<MyPostSummaryResponse>>> getMyPosts(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "탭. 기본 POSTS", example = "POSTS") MyPostTab tab,
			@Parameter(description = "월. 비우면 전체", example = "2026-06") YearMonth yearMonth,
			@Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 비운다") String cursor,
			@Parameter(description = "가져올 개수. 기본 20, 최대 100", example = "20")
			@Min(1) @Max(100) int size);

	@Operation(summary = "타인 게시물 목록",
			description = "차단 관계면 빈 목록을 돌려준다 — 404 로 돌려주면 차단했다는 사실이 드러난다. "
					+ "정렬은 착용일 최신순이라 클라이언트가 월 헤더로 묶을 수 있다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "404", description = "USER_404 — 사용자를 찾을 수 없음")
	})
	ResponseEntity<BaseResponse<CursorResponse<UserPostSummaryResponse>>> getUserPosts(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "사용자 id", example = "1") Long userId,
			@Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 비운다") String cursor,
			@Parameter(description = "가져올 개수. 기본 20, 최대 100", example = "20")
			@Min(1) @Max(100) int size);
}
