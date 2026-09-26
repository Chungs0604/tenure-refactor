package com.tenure.global.mail.smtp;

import com.tenure.global.mail.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** 실제 SMTP 발송. {@code app.mail.type=smtp} 일 때만 올라온다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mail.type", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

	private final JavaMailSender mailSender;

	@Override
	public void sendVerificationCode(String to, String code) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(to);
		message.setSubject("[Tenure] 인증번호를 입력해주세요");
		message.setText("인증번호는 %s 입니다. 3분 안에 입력해주세요.".formatted(code));
		mailSender.send(message);
	}
}
