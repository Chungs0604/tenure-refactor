package com.tenure.global.mail;

/** 메일 발송. 구현을 갈아끼울 수 있게 인터페이스로 둔다 — 테스트는 이걸 mock 한다. */
public interface EmailSender {

	void sendVerificationCode(String to, String code);
}
