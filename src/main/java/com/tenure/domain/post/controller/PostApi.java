package com.tenure.domain.post.controller;

import com.tenure.domain.post.dto.request.PostCreateRequest;
import com.tenure.domain.post.dto.request.PostUpdateRequest;
import com.tenure.domain.post.dto.response.PostCreateResponse;
import com.tenure.domain.post.dto.response.PostIdResponse;
import com.tenure.domain.post.dto.response.PostLikeResponse;
import com.tenure.domain.post.dto.response.PostResponse;
import com.tenure.domain.post.dto.response.PostSaveResponse;
import com.tenure.domain.post.dto.response.PostSummaryResponse;
import com.tenure.domain.post.enums.FeedTab;
import com.tenure.global.pagination.CursorResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/**
 * 파라미터 제약을 이 인터페이스에만 선언한다 — 근거는 UserApi 의 주석에 적어 두었다.
 *
 * 정렬은 서버가 고정한다 — 화면마다 정렬이 하나로 정해져 있고(피드는 게시일, 그리드는 착용일)
 * 임의 정렬을 열면 인덱스를 태울 수 없는 조합이 들어온다.
 *
 * 목록은 커서 방식이다. cursor 는 직전 응답의 nextCursor 를 그대로 넘기고,
 * 첫 페이지에서는 비운다. 형식은 서버만 안다 — 클라이언트가 만들어 보내면 형식을 바꿀 수 없다.
 */
@Validated
@Tag(name = "게시물", description = "OOTD 게시물 API")
public interface PostApi {

	@Operation(summary = "홈 피드",
			description = "모두 / 팔로우 탭. 차단 관계(양방향)와 삭제된 게시물은 빠진다. "
					+ "카드는 대표 사진 1장만 싣고 좋아요·저장 수는 카운터 컬럼을 그대로 읽는다. "
					+ "정렬은 게시일 최신순 고정이며 커서 키도 게시일이다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "400", description = "COMMON_400 — 커서 형식 오류 또는 size 범위 초과")
	})
	ResponseEntity<BaseResponse<CursorResponse<PostSummaryResponse>>> getFeed(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "탭. 기본 ALL", example = "ALL") FeedTab tab,
			@Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 비운다") String cursor,
			@Parameter(description = "가져올 개수. 기본 20, 최대 100", example = "20")
			@Min(1) @Max(100) int size);

	@Operation(summary = "OOTD 상세",
			description = "사진·태그·작성자와 하트·북마크·팔로우의 초기 상태를 함께 준다. "
					+ "태그 말풍선은 판매 중일 때만 '#판매중' 이 붙어 2행이 되므로 saleStatus 를 싣는다. "
					+ "조회 시 최근 열람(Redis)에 기록된다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "404", description = "POST_404 — 게시물을 찾을 수 없음")
	})
	ResponseEntity<BaseResponse<PostResponse>> getPost(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "게시물 id", example = "1") Long postId);

	@Operation(summary = "OOTD 게시",
			description = "사진은 업로드 API 가 돌려준 키로 받는다. 태그 대상은 본인이 현재 보유한 "
					+ "아이템만이며, 게시와 동시에 아이템당 착용 기록(OOTD 인증)을 1건 적립한다. "
					+ "직전 30일 유효 OOTD 가 20개가 되면 그 자리에서 레코드 등급으로 올린다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "게시 성공"),
			@ApiResponse(responseCode = "400", description = "POST_400_TAG_LIMIT — 사진당 태그 5개 초과 / "
					+ "POST_400_TAG_OWNERSHIP — 본인 보유 아이템이 아님 / "
					+ "POST_400_COVER — 대표 사진 중복 지정 / POST_400_IMAGE_ORDER — 사진 순서 중복"),
			@ApiResponse(responseCode = "404", description = "ITEM_404 — 아이템을 찾을 수 없음")
	})
	ResponseEntity<BaseResponse<PostCreateResponse>> create(
			@Parameter(hidden = true) AuthUser authUser,
			@Valid PostCreateRequest request);

	@Operation(summary = "게시물 태그 수정",
			description = "태그를 A→B 로 바꾸면 A 의 착용 기록을 무효화하고 B 에 적립한다. "
					+ "아이템을 비우면 태그를 지운다. 이미 판매돼 소유 구간이 끝난 아이템의 태그는 "
					+ "수정할 수 없다 — 구매자가 보고 산 'OOTD 인증 N회' 가 사후에 줄어든다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "수정 성공"),
			@ApiResponse(responseCode = "400", description = "POST_400_TAG_OWNERSHIP — 본인 보유 아이템이 아님"),
			@ApiResponse(responseCode = "403", description = "POST_403 — 본인의 게시물이 아님"),
			@ApiResponse(responseCode = "404", description = "POST_404 — 게시물을 찾을 수 없음 / "
					+ "POST_404_TAG — 이 게시물의 태그가 아님"),
			@ApiResponse(responseCode = "409", description = "ITEM_409_TAG_CLOSED_PERIOD — 종료된 소유 구간")
	})
	ResponseEntity<BaseResponse<PostIdResponse>> updateTags(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "게시물 id", example = "1") Long postId,
			@Valid PostUpdateRequest request);

	@Operation(summary = "게시물 삭제",
			description = "soft delete 다. 판매 중인 상품의 대표 OOTD 로 걸려 있으면 거부한다. "
					+ "이 게시물이 적립한 착용 기록은 함께 무효화하되 종료된 소유 구간의 것은 남긴다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "삭제 성공"),
			@ApiResponse(responseCode = "403", description = "POST_403 — 본인의 게시물이 아님"),
			@ApiResponse(responseCode = "404", description = "POST_404 — 게시물을 찾을 수 없음"),
			@ApiResponse(responseCode = "409", description = "POST_409_FEATURED — 판매 중 대표 OOTD")
	})
	ResponseEntity<BaseResponse<Void>> delete(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "게시물 id", example = "1") Long postId);

	@Operation(summary = "좋아요", description = "멱등하지 않다. 이미 눌렀으면 409 다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "성공"),
			@ApiResponse(responseCode = "404", description = "POST_404 — 게시물을 찾을 수 없음"),
			@ApiResponse(responseCode = "409", description = "POST_409_LIKE — 이미 좋아요함")
	})
	ResponseEntity<BaseResponse<PostLikeResponse>> like(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "게시물 id", example = "1") Long postId);

	@Operation(summary = "좋아요 취소",
			description = "없는 관계면 조용히 성공 처리한다 — 결과가 어느 쪽이든 '안 누른 상태' 다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "성공"),
			@ApiResponse(responseCode = "404", description = "POST_404 — 게시물을 찾을 수 없음")
	})
	ResponseEntity<BaseResponse<PostLikeResponse>> unlike(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "게시물 id", example = "1") Long postId);

	@Operation(summary = "저장", description = "멱등하지 않다. 이미 저장했으면 409 다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "성공"),
			@ApiResponse(responseCode = "404", description = "POST_404 — 게시물을 찾을 수 없음"),
			@ApiResponse(responseCode = "409", description = "POST_409_SAVE — 이미 저장함")
	})
	ResponseEntity<BaseResponse<PostSaveResponse>> save(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "게시물 id", example = "1") Long postId);

	@Operation(summary = "저장 취소", description = "없는 관계면 조용히 성공 처리한다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "성공"),
			@ApiResponse(responseCode = "404", description = "POST_404 — 게시물을 찾을 수 없음")
	})
	ResponseEntity<BaseResponse<PostSaveResponse>> unsave(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "게시물 id", example = "1") Long postId);
}
