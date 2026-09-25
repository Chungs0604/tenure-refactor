package com.tenure.domain.notification.exception;

import com.tenure.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

	NOTIFICATION_404(HttpStatus.NOT_FOUND, "NOTIFICATION_404", "알림을 찾을 수 없습니다."),
	NOTIFICATION_403(HttpStatus.FORBIDDEN, "NOTIFICATION_403", "본인의 알림이 아닙니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
