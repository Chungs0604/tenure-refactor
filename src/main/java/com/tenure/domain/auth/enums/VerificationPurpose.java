package com.tenure.domain.auth.enums;

/**
 * 회원가입과 비밀번호 찾기 두 섹션 모두 '인증번호' 입력 단계를 가진다. 어느 컬럼도 쓰지 않는다 — Redis 키 email-verify:{purpose}:{email} 의 일부다 (D…
 */
public enum VerificationPurpose {

	SIGN_UP,
	PASSWORD_RESET;

}
