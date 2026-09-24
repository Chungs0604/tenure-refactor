package com.tenure.global.exception;

import com.tenure.global.response.BaseResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * HTTP 요청 경로의 예외를 {@link BaseResponse} 로 변환한다.
 *
 * <p>예외 로깅은 여기서 한 번만 한다 — 던지는 쪽에서 찍으면 호출 스택마다 중복된다.
 * 4xx 는 스택트레이스를 남기지 않는다. 로그량이 감당되지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(CustomException.class)
	public ResponseEntity<BaseResponse<Void>> handleCustom(CustomException e) {
		ErrorCode code = e.getErrorCode();
		logByStatus(code, e);
		return ResponseEntity.status(code.getStatus()).body(BaseResponse.fail(code));
	}

	/** {@code @Valid} 실패. 어느 필드가 왜 틀렸는지 알려줘야 고칠 수 있다. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<BaseResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
		String detail = e.getBindingResult().getFieldErrors().stream()
				.map(f -> "%s: %s".formatted(f.getField(), defaultMessage(f)))
				.reduce((a, b) -> a + ", " + b)
				.orElse(CommonErrorCode.BAD_REQUEST.getMessage());
		log.warn("[{}] {}", CommonErrorCode.BAD_REQUEST.getCode(), detail);
		return ResponseEntity.status(CommonErrorCode.BAD_REQUEST.getStatus())
				.body(BaseResponse.fail(CommonErrorCode.BAD_REQUEST, detail));
	}

	/** 잘못된 타입·누락된 파라미터·깨진 JSON 은 전부 400 이다. */
	@ExceptionHandler({
			ConstraintViolationException.class,
			HttpMessageNotReadableException.class,
			MissingServletRequestParameterException.class,
			MethodArgumentTypeMismatchException.class
	})
	public ResponseEntity<BaseResponse<Void>> handleBadRequest(Exception e) {
		log.warn("[{}] {}", CommonErrorCode.BAD_REQUEST.getCode(), e.getMessage());
		return ResponseEntity.status(CommonErrorCode.BAD_REQUEST.getStatus())
				.body(BaseResponse.fail(CommonErrorCode.BAD_REQUEST));
	}

	/**
	 * Spring Security 예외. 필터 단계에서 터진 것은 EntryPoint 가 처리하고,
	 * 여기 오는 것은 메서드 보안에서 터진 것이다.
	 */
	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<BaseResponse<Void>> handleAuthentication(AuthenticationException e) {
		log.warn("[{}] {}", CommonErrorCode.UNAUTHORIZED.getCode(), e.getMessage());
		return ResponseEntity.status(CommonErrorCode.UNAUTHORIZED.getStatus())
				.body(BaseResponse.fail(CommonErrorCode.UNAUTHORIZED));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<BaseResponse<Void>> handleAccessDenied(AccessDeniedException e) {
		log.warn("[{}] {}", CommonErrorCode.FORBIDDEN.getCode(), e.getMessage());
		return ResponseEntity.status(CommonErrorCode.FORBIDDEN.getStatus())
				.body(BaseResponse.fail(CommonErrorCode.FORBIDDEN));
	}

	/** 매핑되지 않은 경로. 스택트레이스 없이 404 로 끝낸다. */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<BaseResponse<Void>> handleNoResource(NoResourceFoundException e) {
		log.warn("[{}] {}", CommonErrorCode.NOT_FOUND.getCode(), e.getResourcePath());
		return ResponseEntity.status(CommonErrorCode.NOT_FOUND.getStatus())
				.body(BaseResponse.fail(CommonErrorCode.NOT_FOUND));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<BaseResponse<Void>> handleUnexpected(Exception e) {
		log.error("[{}] 처리되지 않은 예외", CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(), e);
		return ResponseEntity.status(CommonErrorCode.INTERNAL_SERVER_ERROR.getStatus())
				.body(BaseResponse.fail(CommonErrorCode.INTERNAL_SERVER_ERROR));
	}

	private void logByStatus(ErrorCode code, Exception e) {
		if (code.getStatus().is5xxServerError()) {
			log.error("[{}] {}", code.getCode(), code.getMessage(), e);
		} else {
			log.warn("[{}] {}", code.getCode(), code.getMessage());
		}
	}

	private String defaultMessage(FieldError error) {
		return error.getDefaultMessage() != null ? error.getDefaultMessage() : "값이 올바르지 않습니다.";
	}
}
