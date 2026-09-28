package com.tenure.domain.payout.dto.response;

import com.tenure.domain.payout.enums.BankCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 정산 계좌 한 건. 목록은 기본 계좌를 맨 위에 두고 최근 등록 순으로 내려준다.
 *
 * 계좌번호를 마스킹하지 않는다 — 수정 화면이 이 값으로 폼을 채우므로 가리면 수정이 깨진다.
 * 본인 계좌만 내려주므로 가릴 대상도 아니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "정산 계좌")
public class PayoutAccountResponse {

	@Schema(description = "정산 계좌 id")
	private final Long id;

	@Schema(description = "은행 enum 값", example = "TOSS_BANK")
	private final BankCode bankCode;

	@Schema(description = "은행 표시명", example = "토스뱅크")
	private final String bankName;

	@Schema(description = "예금주", example = "홍길동")
	private final String accountHolder;

	@Schema(description = "계좌 번호", example = "123456-789012")
	private final String accountNumber;

	@Schema(description = "기본 계좌면 true")
	private final Boolean isDefault;
}
