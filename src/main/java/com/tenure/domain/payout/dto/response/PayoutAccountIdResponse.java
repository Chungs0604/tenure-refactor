package com.tenure.domain.payout.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 등록·수정 결과. 둘 다 id 만 돌려주므로 한 타입을 쓴다 — 화면은 뒤이어 목록을 다시 조회한다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "정산 계좌 id")
public class PayoutAccountIdResponse {

	@Schema(description = "정산 계좌 id")
	private final Long id;
}
