package com.tenure.domain.order.dto.request;

import com.tenure.domain.order.enums.CourierCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운송장 번호 입력 요청.
 *
 * 이 엔드포인트의 에러 표면은 ORDER_400_TRACKING 하나다 — bean validation 이 아니라
 * OrderService 가 직접 형식을 확인하고 던진다. 그래서 여기엔 제약 애너테이션을 달지 않는다
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "운송장 번호 입력 요청")
public class ShipmentUpdateRequest {

	@Schema(description = "택배사", example = "CJ_LOGISTICS")
	private CourierCode courierCode;

	@Schema(description = "운송장 번호. 숫자만", example = "1234123412349")
	private String trackingNo;
}
