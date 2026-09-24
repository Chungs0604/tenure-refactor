package com.tenure.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tenure.global.exception.CommonErrorCode;
import com.tenure.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 모든 API 의 공통 응답 봉투. 성공·실패 모두 이 형태로 내려간다.
 *
 * <p>record 가 아니라 클래스다 — 필드 순서를 고정해 직렬화 결과를 안정적으로 유지한다.
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "공통 응답 봉투")
public class BaseResponse<T> {

	@Schema(description = "성공 여부", example = "true")
	private final boolean success;

	@Schema(description = "응답 코드", example = "COMMON_200")
	private final String code;

	@Schema(description = "응답 메시지", example = "요청에 성공했습니다.")
	private final String message;

	@Schema(description = "응답 본문. 실패면 null")
	private final T data;

	public static <T> BaseResponse<T> success(T data) {
		CommonErrorCode ok = CommonErrorCode.OK;
		return new BaseResponse<>(true, ok.getCode(), ok.getMessage(), data);
	}

	public static BaseResponse<Void> success() {
		return success(null);
	}

	public static BaseResponse<Void> fail(ErrorCode errorCode) {
		return new BaseResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
	}

	/** 검증 실패처럼 어느 필드가 틀렸는지 알려줘야 할 때만 메시지를 바꾼다. */
	public static BaseResponse<Void> fail(ErrorCode errorCode, String message) {
		return new BaseResponse<>(false, errorCode.getCode(), message, null);
	}
}
