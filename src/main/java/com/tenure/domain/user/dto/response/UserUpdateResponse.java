package com.tenure.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 프로필 수정 결과. 화면은 수정 후 프로필을 다시 조회하므로 id 만 돌려준다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "프로필 수정 결과")
public class UserUpdateResponse {

	@Schema(description = "수정된 사용자 id")
	private final Long id;
}
