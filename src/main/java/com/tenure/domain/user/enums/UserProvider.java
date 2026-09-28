package com.tenure.domain.user.enums;

/**
 * 계정의 출처. 로그인 화면(2791:15250)의 '다음' 구분선 아래 구글·네이버 아이콘이 근거다 (DEC-017).
 *
 * <p>{@link #LOCAL} 은 이메일·비밀번호 가입이라 {@code password_hash} 를 갖고 {@code provider_id} 가 없다.
 * 소셜 가입은 반대다 — 비밀번호가 없고 제공자 식별자를 갖는다.
 */
public enum UserProvider {

	/** 이메일·비밀번호 가입 */
	LOCAL,
	/** 구글 */
	GOOGLE,
	/** 네이버 */
	NAVER;

	public boolean isLocal() {
		return this == LOCAL;
	}
}
