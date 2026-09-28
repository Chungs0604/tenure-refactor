package com.tenure.domain.payout.dto.response;

import com.tenure.domain.payout.enums.BankCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 은행 드롭다운의 선택지 한 건. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "은행")
public class BankResponse {

	@Schema(description = "저장·전송하는 값", example = "TOSS_BANK")
	private final BankCode code;

	@Schema(description = "화면 표시명", example = "토스뱅크")
	private final String name;

	public static BankResponse from(BankCode bankCode) {
		return BankResponse.builder()
				.code(bankCode)
				.name(bankCode.getBankName())
				.build();
	}
}
