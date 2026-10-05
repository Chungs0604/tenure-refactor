package com.tenure.domain.order.dto.response;

import com.tenure.domain.payment.enums.PaymentMethod;
import com.tenure.domain.payment.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "결제 정보")
public class OrderPaymentResponse {

	@Schema(description = "결제 수단", example = "TOSS_PAY")
	private final PaymentMethod method;

	@Schema(description = "결제 상태", example = "CAPTURED")
	private final PaymentStatus status;
}
