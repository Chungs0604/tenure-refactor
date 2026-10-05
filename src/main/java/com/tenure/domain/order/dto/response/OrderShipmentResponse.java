package com.tenure.domain.order.dto.response;

import com.tenure.domain.address.enums.DeliveryRequestType;
import com.tenure.domain.order.enums.CourierCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "배송 정보")
public class OrderShipmentResponse {

	@Schema(description = "택배사. 운송장 입력 전엔 null", example = "CJ_LOGISTICS")
	private final CourierCode courierCode;

	@Schema(description = "운송장 번호. 운송장 입력 전엔 null", example = "1234123412349")
	private final String trackingNo;

	@Schema(description = "배송 조회 버튼 이동 주소. 운송장 입력 전엔 null")
	private final String trackingUrl;

	@Schema(description = "수령인", example = "홍길동")
	private final String recipientName;

	@Schema(description = "연락처", example = "010-1234-5678")
	private final String phone;

	@Schema(description = "주소", example = "서울특별시 동작구 상도로 369")
	private final String address;

	@Schema(description = "상세주소", example = "112동 201호")
	private final String addressDetail;

	@Schema(description = "배송 요청사항 유형", example = "AT_DOOR")
	private final DeliveryRequestType deliveryRequestType;

	@Schema(description = "DIRECT 일 때의 요청사항 입력값", example = "배송 전에 미리 연락 바랍니다.")
	private final String deliveryRequest;
}
