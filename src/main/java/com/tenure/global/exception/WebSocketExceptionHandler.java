package com.tenure.global.exception;

import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * STOMP 메시지 매핑 경로의 예외를 발신자 본인에게 알림.
 *
 * GlobalExceptionHandler 가 HTTP 경로를 한곳에 모으는 것과 같은 이유로 여기도 도메인
 * 컨트롤러가 아니라 전역에 둠 — ControllerAdvice 의 MessageExceptionHandler 는 도메인
 * 컨트롤러 안에 둔 것과 달리 모든 메시지 매핑 컨트롤러에 적용됨.
 *
 * ControllerAdvice 는 WebMvcTest 슬라이스에도 무조건 함께 뜸 — REST 전용 슬라이스
 * 테스트는 SimpMessagingTemplate 빈이 없어 그대로 주입하면 전부 깨짐. ObjectProvider 로
 * 늦게 찾아서, 빈이 없는 컨텍스트에서도 컨트롤러 자체는 뜨게 함 — 그 컨텍스트에는 애초에
 * WS 요청이 없으니 실제로 찾지 못할 일도 없음.
 *
 * 로깅 기준은 GlobalExceptionHandler 와 같음 — CustomException 은 상태별로 warn/error 를
 * 가르고, 그 외는 스택트레이스와 함께 error 로 남김.
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class WebSocketExceptionHandler {

	private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;

	@MessageExceptionHandler(CustomException.class)
	public void handleCustom(CustomException e, Principal principal) {
		ErrorCode code = e.getErrorCode();
		logByStatus(code, e);
		sendErrorIfAuthenticated(principal, code.getCode() + ": " + e.getMessage());
	}

	/**
	 * CustomException 이 아닌 나머지(검증 실패·역직렬화 오류 등) — 잡아 두지 않으면 조용히
	 * 사라져서 클라이언트가 전송 실패를 알 길이 없음. StompAuthChannelInterceptor 가 CONNECT
	 * 없는 프레임을 막아 principal 이 null 인 경우는 정상적으로는 여기 도달하지 않지만,
	 * 방어적으로 한 번 더 확인함.
	 */
	@MessageExceptionHandler(Exception.class)
	public void handleUnexpected(Exception e, Principal principal) {
		log.error("[{}] WS 메시지 처리 중 처리되지 않은 예외", CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(), e);
		sendErrorIfAuthenticated(principal, CommonErrorCode.INTERNAL_SERVER_ERROR.getCode() + ": 요청을 처리할 수 없습니다.");
	}

	private void sendErrorIfAuthenticated(Principal principal, String payload) {
		SimpMessagingTemplate messagingTemplate = messagingTemplateProvider.getIfAvailable();
		if (principal == null || messagingTemplate == null) {
			return;
		}
		messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/errors", payload);
	}

	private void logByStatus(ErrorCode code, Exception e) {
		if (code.getStatus().is5xxServerError()) {
			log.error("[{}] {}", code.getCode(), code.getMessage(), e);
		} else {
			log.warn("[{}] {}", code.getCode(), code.getMessage());
		}
	}
}
