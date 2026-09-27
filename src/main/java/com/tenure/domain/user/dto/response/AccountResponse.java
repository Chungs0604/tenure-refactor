package com.tenure.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 계정 정보. 프로필(닉네임·체형)과 다른 값이다 — 화면도 '계정 정보'와 '프로필 수정'으로 갈라져 있다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "계정 정보")
public class AccountResponse {

	@Schema(description = "실명", example = "홍길동")
	private final String realName;

	@Schema(description = "휴대폰번호", example = "821012345678")
	private final String phone;

	@Schema(description = "계정 이메일", example = "gildong@naver.com")
	private final String email;
}
