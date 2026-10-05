package com.tenure.domain.order.dto.response;

import com.tenure.domain.order.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "거래 타임라인 한 단계")
public class OrderStatusHistoryResponse {

	@Schema(description = "단계", example = "PAYMENT_COMPLETED")
	private final OrderStatus status;

	@Schema(description = "단계 도달 시각", example = "2026-05-26T19:50:23")
	private final LocalDateTime changedAt;
}
