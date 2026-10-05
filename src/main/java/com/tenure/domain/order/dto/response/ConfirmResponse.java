package com.tenure.domain.order.dto.response;

import com.tenure.domain.order.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "구매 확정 결과")
public class ConfirmResponse {

	@Schema(description = "PURCHASE_CONFIRMED 로 전이", example = "PURCHASE_CONFIRMED")
	private final OrderStatus status;

	@Schema(description = "정산 예정일 = 확정일 +2영업일", example = "2026-06-04")
	private final LocalDate settlementScheduledAt;
}
