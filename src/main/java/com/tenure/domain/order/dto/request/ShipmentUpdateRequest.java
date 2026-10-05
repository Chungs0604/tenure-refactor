package com.tenure.domain.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운송장 번호 입력 요청.
 *
 * 이 엔드포인트의 에러 표면은 ORDER_400_TRACKING 하나다 — bean validation 도, Jackson 의
 * enum 역직렬화도 쓰지 않는다. courierCode 를 enum 으로 받으면 잘못된 값이 컨트롤러 진입
 * 전에 HttpMessageNotReadableException 으로 번역돼 COMMON_400 이 나가버린다 — 문서화된
 * 에러 표면과 어긋난다. 그래서 문자열로 받고 OrderService 가 직접 enum 변환까지 확인한다
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "운송장 번호 입력 요청")
public class ShipmentUpdateRequest {

	@Schema(description = "택배사", example = "CJ_LOGISTICS")
	private String courierCode;

	@Schema(description = "운송장 번호. 숫자만", example = "1234123412349")
	private String trackingNo;
}
