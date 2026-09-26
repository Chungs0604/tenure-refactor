package com.tenure.global.mail.log;

import com.tenure.global.mail.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로컬 기본값. 메일을 보내지 않고 로그로 찍는다.
 *
 * <p>SMTP 자격증명 없이 가입 플로우를 끝까지 돌려볼 수 있어야 한다.
 * 운영에서는 {@code MAIL_TYPE=smtp} 로 바꾼다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.mail.type", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

	@Override
	public void sendVerificationCode(String to, String code) {
		log.info("[메일] 수신자={} 인증번호={} (실제로 보내지 않는다)", to, code);
	}
}
