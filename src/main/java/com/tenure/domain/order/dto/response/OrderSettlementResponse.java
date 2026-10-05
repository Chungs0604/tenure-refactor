package com.tenure.domain.order.dto.response;

import com.tenure.domain.payout.enums.BankCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** viewerRole=SELLER 일 때만 채워진다 — 구매자에게 판매자의 정산 계좌를 보일 이유가 없다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "정산 정보. SELLER 시점에만 채워진다")
public class OrderSettlementResponse {

	@Schema(description = "정산 계좌 은행", example = "TOSS_BANK")
	private final BankCode bankCode;

	@Schema(description = "정산 계좌 번호", example = "1111-1111-1111")
	private final String accountNumber;

	@Schema(description = "정산 완료 시각. 정산 전엔 null", example = "2026-06-24T10:00:00")
	private final LocalDateTime settledAt;
}
