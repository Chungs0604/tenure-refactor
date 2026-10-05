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
@Schema(description = "거래 취소 결과")
public class CancelResponse {

	@Schema(description = "CANCELED 로 전이", example = "CANCELED")
	private final OrderStatus status;

	@Schema(description = "환불 처리 시각", example = "2026-05-27T09:00:00")
	private final LocalDateTime refundedAt;
}
