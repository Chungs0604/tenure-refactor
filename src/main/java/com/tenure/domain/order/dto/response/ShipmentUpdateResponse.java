package com.tenure.domain.order.dto.response;

import com.tenure.domain.order.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "운송장 번호 입력 결과")
public class ShipmentUpdateResponse {

	@Schema(description = "SHIPPED 로 전이", example = "SHIPPED")
	private final OrderStatus status;
}
