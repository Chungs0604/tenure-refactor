package com.tenure.domain.address.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 배송 요청사항. 배송지 추가 화면의 '배송 요청사항(선택)' 드롭다운이다.
 *
 * <p>{@link #DIRECT} 를 고르면 텍스트 필드가 열리고 화면이 '최대 50자까지 입력 가능' 을 안내한다.
 * 나머지 셋은 문구가 고정이라 {@code delivery_request} 를 비운다.
 */
@Getter
@RequiredArgsConstructor
public enum DeliveryRequestType {

	AT_DOOR("문 앞에 놓아주세요."),
	SECURITY_OFFICE("경비실에 맡겨주세요."),
	PARCEL_BOX("택배함에 넣어주세요."),
	DIRECT("직접 입력");

	/** 드롭다운에 노출하는 문구. DIRECT 만 사용자가 직접 쓴다. */
	private final String label;

	public boolean isDirect() {
		return this == DIRECT;
	}
}
