package com.tenure.domain.auth.controller;

import com.tenure.domain.auth.dto.request.EmailVerificationConfirmRequest;
import com.tenure.domain.auth.dto.request.EmailVerificationSendRequest;
import com.tenure.domain.auth.dto.request.LoginRequest;
import com.tenure.domain.auth.dto.request.PasswordResetRequest;
import com.tenure.domain.auth.dto.request.TokenReissueRequest;
import com.tenure.domain.auth.dto.response.EmailVerificationConfirmResponse;
import com.tenure.domain.auth.dto.response.EmailVerificationSendResponse;
import com.tenure.domain.auth.dto.response.LoginResponse;
import com.tenure.domain.auth.dto.response.TokenReissueResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

/**
 * 파라미터 제약({@code @Valid} 포함)을 <b>이 인터페이스에만</b> 선언한다 — {@code UserApi} 와 같은 이유다.
 * 구현 클래스에 다시 달면 Hibernate Validator 가 {@code HV000151} 로 거부한다.
 *
 * <p>{@code @Validated} 는 붙이지 않았다. 여기에는 메서드 파라미터에 직접 걸린 제약이 없고
 * {@code @RequestBody} 의 {@code @Valid} 는 인자 해석 단계에서 처리되므로 메서드 검증이 필요 없다.
 * {@code @RequestParam @NotBlank} 같은 제약을 추가하는 순간 {@code @Validated} 도 함께 붙여야 한다.
 */
@Tag(name = "인증", description = "로그인·토큰·이메일 인증 API")
public interface AuthApi {

	@Operation(summary = "로그인",
			description = "이메일·비밀번호로 로그인한다. `keepLoggedIn` 을 켜면 리프레시 토큰 수명이 길어진다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "로그인 성공"),
			@ApiResponse(responseCode = "401", description = "AUTH_401_CREDENTIAL — 비밀번호 불일치"),
			@ApiResponse(responseCode = "404", description = "AUTH_404_EMAIL — 가입되지 않은 이메일")
	})
	@SecurityRequirements
	ResponseEntity<BaseResponse<LoginResponse>> login(@Valid LoginRequest request);

	@Operation(summary = "로그아웃",
			description = "이 사용자의 리프레시 토큰을 전부 무효화한다. 본문은 없다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "로그아웃 성공"),
			@ApiResponse(responseCode = "401", description = "COMMON_401 — 인증 필요")
	})
	ResponseEntity<BaseResponse<Void>> logout(
			@Parameter(hidden = true) AuthUser authUser);

	@Operation(summary = "액세스 토큰 재발급",
			description = "리프레시 토큰으로 새 액세스 토큰을 받는다. 리프레시 토큰은 교체하지 않는다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "재발급 성공"),
			@ApiResponse(responseCode = "401", description = "AUTH_401_TOKEN — 만료·위조·이미 로그아웃된 토큰")
	})
	@SecurityRequirements
	ResponseEntity<BaseResponse<TokenReissueResponse>> reissue(@Valid TokenReissueRequest request);

	@Operation(summary = "인증번호 발송", description = "가입·비밀번호 찾기용 인증번호를 메일로 보낸다. 수명은 3분이다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "발송 성공"),
			@ApiResponse(responseCode = "404", description = "AUTH_404_EMAIL — 비밀번호 찾기인데 미가입"),
			@ApiResponse(responseCode = "409", description = "USER_409_EMAIL — 가입인데 이미 존재")
	})
	@SecurityRequirements
	ResponseEntity<BaseResponse<EmailVerificationSendResponse>> sendCode(
			@Valid EmailVerificationSendRequest request);

	@Operation(summary = "인증번호 확인",
			description = "인증번호가 맞으면 1회용 토큰을 발급한다. 수명은 10분이고 한 번만 쓸 수 있다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "확인 성공"),
			@ApiResponse(responseCode = "400", description = "AUTH_400_CODE — 인증번호 불일치"),
			@ApiResponse(responseCode = "410", description = "AUTH_410_CODE — 인증번호 만료")
	})
	@SecurityRequirements
	ResponseEntity<BaseResponse<EmailVerificationConfirmResponse>> confirmCode(
			@Valid EmailVerificationConfirmRequest request);

	@Operation(summary = "비밀번호 재설정",
			description = "인증번호 확인으로 받은 1회용 토큰으로 새 비밀번호를 설정한다. "
					+ "토큰의 용도가 PASSWORD_RESET 이어야 하고 한 번만 쓸 수 있다. "
					+ "재설정하면 기존 리프레시 토큰이 전부 무효화되어 모든 기기에서 다시 로그인해야 한다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "재설정 성공"),
			@ApiResponse(responseCode = "400", description = "USER_400_PASSWORD · AUTH_400_PURPOSE_MISMATCH"),
			@ApiResponse(responseCode = "401", description = "AUTH_401_VERIFICATION_TOKEN — 대상이 다른 토큰"),
			@ApiResponse(responseCode = "409", description = "AUTH_409_TOKEN_CONSUMED — 이미 사용된 토큰"),
			@ApiResponse(responseCode = "410", description = "AUTH_410_VERIFICATION_TOKEN — 인증 토큰 만료")
	})
	@SecurityRequirements
	ResponseEntity<BaseResponse<Void>> resetPassword(@Valid PasswordResetRequest request);
}
