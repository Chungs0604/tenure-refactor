package com.tenure.domain.order.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 운송장 번호 입력 > '택배사' Select box 펼친 화면(2958:68218)의 4개 항목
 */
@Getter
@RequiredArgsConstructor
public enum CourierCode {

	CJ_LOGISTICS("CJ대한통운"),
	KOREA_POST("우체국택배"),
	GS_POSTBOX("GS Postbox"),
	CU_CONVENIENCE("CU 편의점택배");

	/** 드롭다운과 목록에 노출하는 이름. */
	private final String courierName;
}
