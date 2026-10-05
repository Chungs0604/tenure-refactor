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
@Schema(description = "배송 완료 처리 결과")
public class DeliverResponse {

	@Schema(description = "DELIVERED 로 전이", example = "DELIVERED")
	private final OrderStatus status;

	@Schema(description = "자동 구매 확정 시각 = 배송 완료 +72h", example = "2026-06-04T10:00:00")
	private final LocalDateTime autoConfirmAt;
}
