package com.tenure.domain.payout.controller;

import com.tenure.domain.payout.dto.request.PayoutAccountCreateRequest;
import com.tenure.domain.payout.dto.request.PayoutAccountUpdateRequest;
import com.tenure.domain.payout.dto.response.BankResponse;
import com.tenure.domain.payout.dto.response.PayoutAccountIdResponse;
import com.tenure.domain.payout.dto.response.PayoutAccountResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/**
 * 파라미터 제약을 이 인터페이스에만 선언한다 — 근거는 {@code UserApi} 의 주석에 적어 두었다.
 */
@Validated
@Tag(name = "정산 계좌", description = "정산 계좌 API")
public interface PayoutApi {

	@Operation(summary = "정산 계좌 목록",
			description = "기본 계좌가 맨 위, 그다음 최근 등록 순이다. 개수가 적어 페이징하지 않는다. "
					+ "계좌번호는 수정 화면의 입력값이 되므로 마스킹하지 않는다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	ResponseEntity<BaseResponse<List<PayoutAccountResponse>>> getAccounts(
			@Parameter(hidden = true) AuthUser authUser);

	@Operation(summary = "은행 목록",
			description = "정산 계좌 추가·수정 화면의 은행 드롭다운 선택지다. "
					+ "화면이 한글 표시명을 하드코딩하면 은행이 늘었을 때 따라오지 못한다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	ResponseEntity<BaseResponse<List<BankResponse>>> getBanks(
			@Parameter(hidden = true) AuthUser authUser);

	@Operation(summary = "정산 계좌 등록",
			description = "첫 계좌는 자동으로 기본이 된다. 추가 화면에 기본 설정 UI 가 없어 "
					+ "요청에서 기본 여부를 받지 않는다.")
	@ApiResponse(responseCode = "200", description = "등록 성공")
	ResponseEntity<BaseResponse<PayoutAccountIdResponse>> create(
			@Parameter(hidden = true) AuthUser authUser,
			@Valid PayoutAccountCreateRequest request);

	@Operation(summary = "정산 계좌 수정",
			description = "보낸 값으로 전체를 교체한다. 확정된 정산은 settlement 스냅샷을 쓰므로 영향이 없다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "수정 성공"),
			@ApiResponse(responseCode = "403", description = "PAYOUT_403 — 본인의 정산 계좌가 아님"),
			@ApiResponse(responseCode = "404", description = "PAYOUT_404 — 정산 계좌를 찾을 수 없음")
	})
	ResponseEntity<BaseResponse<PayoutAccountIdResponse>> update(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "정산 계좌 id", example = "1") Long accountId,
			@Valid PayoutAccountUpdateRequest request);

	@Operation(summary = "정산 계좌 삭제",
			description = "하드 삭제다. 기본 계좌는 다른 계좌를 기본으로 지정한 뒤에 지울 수 있고, "
					+ "입금이 끝나지 않은 정산이 남아 있으면 지울 수 없다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "삭제 성공"),
			@ApiResponse(responseCode = "403", description = "PAYOUT_403 — 본인의 정산 계좌가 아님"),
			@ApiResponse(responseCode = "404", description = "PAYOUT_404 — 정산 계좌를 찾을 수 없음"),
			@ApiResponse(responseCode = "409",
					description = "PAYOUT_409_LAST_DEFAULT — 기본 계좌 / "
							+ "PAYOUT_409_PENDING_SETTLEMENT — 정산 대기 중")
	})
	ResponseEntity<BaseResponse<Void>> delete(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "정산 계좌 id", example = "1") Long accountId);

	@Operation(summary = "기본 정산 계좌 지정",
			description = "목록에서 라디오로 고르고 '변경하기' 를 누르면 호출한다. "
					+ "기존 기본 계좌를 내리고 지정하며, 이미 기본이면 아무것도 바뀌지 않는다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "지정 성공"),
			@ApiResponse(responseCode = "403", description = "PAYOUT_403 — 본인의 정산 계좌가 아님"),
			@ApiResponse(responseCode = "404", description = "PAYOUT_404 — 정산 계좌를 찾을 수 없음")
	})
	ResponseEntity<BaseResponse<Void>> changeDefault(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "정산 계좌 id", example = "1") Long accountId);
}
