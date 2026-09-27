package com.tenure.domain.user.controller;

import com.tenure.domain.user.dto.request.UserCreateRequest;
import com.tenure.domain.user.dto.request.UserUpdateRequest;
import com.tenure.domain.user.dto.response.AccountResponse;
import com.tenure.domain.user.dto.response.AvailabilityResponse;
import com.tenure.domain.user.dto.response.MyProfileResponse;
import com.tenure.domain.user.dto.response.UserCreateResponse;
import com.tenure.domain.user.dto.response.UserProfileResponse;
import com.tenure.domain.user.dto.response.UserUpdateResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/**
 * 파라미터 제약을 <b>이 인터페이스에만</b> 선언한다.
 *
 * <p>Bean Validation 은 하위 타입이 상위의 파라미터 제약을 다시 정의하는 것을 금지한다
 * (리스코프 치환 — 구현이 조건을 더 좁히면 인터페이스 계약이 거짓이 된다).
 * 구현 클래스에 같은 제약을 달면 Hibernate Validator 가 {@code HV000151} 로 거부하고
 * 해당 컨트롤러의 <b>모든 요청이 500</b> 이 된다.
 *
 * <p>{@code @RequestParam}·{@code @RequestBody} 같은 바인딩 애노테이션은 검증 대상이 아니라
 * 구현 쪽에 남는다 — Spring 이 인터페이스와 구현의 파라미터 애노테이션을 합쳐서 본다.
 *
 * <p>{@code @Validated} 도 여기 붙는다. 제약이 이 인터페이스에 선언되므로 그것을 켜는 스위치도
 * 같은 자리에 있어야 짝이 보인다. 구현 클래스에 둬도 동작하지만
 * ({@code MethodValidationPostProcessor} 가 인터페이스까지 찾는다) 구현이 늘었을 때
 * 새 구현에 붙이는 것을 잊으면 <b>검증이 조용히 사라진다</b>.
 */
@Validated
@Tag(name = "사용자", description = "가입·중복 확인·프로필 API")
public interface UserApi {

	@Operation(summary = "이메일 중복 확인", description = "중복이어도 예외를 던지지 않고 available 로만 답한다.")
	@ApiResponse(responseCode = "200", description = "확인 성공")
	@SecurityRequirements
	ResponseEntity<BaseResponse<AvailabilityResponse>> checkEmail(
			@Parameter(description = "중복 확인할 이메일", example = "gildong@naver.com")
			@NotBlank @Email String email);

	@Operation(summary = "닉네임 중복 확인", description = "중복이어도 예외를 던지지 않고 available 로만 답한다.")
	@ApiResponse(responseCode = "200", description = "확인 성공")
	@SecurityRequirements
	ResponseEntity<BaseResponse<AvailabilityResponse>> checkNickname(
			@Parameter(description = "중복 확인할 닉네임", example = "gildong")
			@NotBlank String nickname);

	@Operation(summary = "회원가입",
			description = "약관 3종 동의와 기본 배송지를 함께 받는다. 인증번호 확인(API-005)으로 받은 "
					+ "1회용 토큰이 필요하며 가입 직후 자동 로그인된다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "가입 성공"),
			@ApiResponse(responseCode = "400", description = "USER_400_PASSWORD · USER_400_AGREEMENT · AUTH_400_PURPOSE_MISMATCH"),
			@ApiResponse(responseCode = "401", description = "AUTH_401_VERIFICATION_TOKEN — 유효하지 않은 인증 토큰"),
			@ApiResponse(responseCode = "409", description = "USER_409_EMAIL · USER_409_NICKNAME · AUTH_409_TOKEN_CONSUMED"),
			@ApiResponse(responseCode = "410", description = "AUTH_410_VERIFICATION_TOKEN — 인증 토큰 만료")
	})
	@SecurityRequirements
	ResponseEntity<BaseResponse<UserCreateResponse>> signUp(@Valid UserCreateRequest request);

	@Operation(summary = "계정 정보 조회", description = "실명·휴대폰번호·계정 이메일. 프로필과 다른 값이다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	ResponseEntity<BaseResponse<AccountResponse>> getMyAccount(
			@Parameter(hidden = true) AuthUser authUser);

	@Operation(summary = "내 프로필 조회",
			description = "프로필과 카운터 4종(팔로워·게시물·아이템·위시)을 함께 내려준다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	ResponseEntity<BaseResponse<MyProfileResponse>> getMyProfile(
			@Parameter(hidden = true) AuthUser authUser);

	@Operation(summary = "프로필 수정",
			description = "사진만 선택이다. profileImageKey 를 null 로 보내면 기본 이미지로 되돌린다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "수정 성공"),
			@ApiResponse(responseCode = "409", description = "USER_409_NICKNAME — 중복된 닉네임")
	})
	ResponseEntity<BaseResponse<UserUpdateResponse>> updateProfile(
			@Parameter(hidden = true) AuthUser authUser,
			@Valid UserUpdateRequest request);

	@Operation(summary = "타인 프로필 조회",
			description = "카운터 3종과 팔로우·차단 상태를 내려준다. 성별은 싣지 않는다. "
					+ "조회하면 '최근 본 사용자' 에 기록된다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "404", description = "USER_404 — 사용자를 찾을 수 없음")
	})
	ResponseEntity<BaseResponse<UserProfileResponse>> getUserProfile(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "조회할 사용자 id", example = "1") Long userId);
}


