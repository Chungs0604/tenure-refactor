package com.tenure.domain.category.enums;

/**
 * 실측 항목. item_measurement 의 10개 고정 컬럼에 1:1 로 대응한다.
 *
 * item 이 아니라 category 패키지에 둔다 — 어느 항목을 받을지 정하는 주체가 대분류이고
 * (MeasurementScheme), 여기 두지 않으면 category 가 item 을 참조해 패키지 순환이 생긴다.
 * item 이 category 를 참조하는 방향은 Item.category 로 이미 열려 있다.
 *
 * 이 이름이 그대로 API 의 요청·응답 키가 된다. 프론트는 API-090 의 measurementFields 로
 * 받은 목록대로 입력란을 그리고 같은 키로 돌려보낸다.
 */
public enum MeasurementField {

	/** 어깨 너비 */
	SHOULDER_WIDTH,
	/** 가슴 단면 */
	CHEST_WIDTH,
	/** 소매길이 */
	SLEEVE_LENGTH,
	/** 총 기장 */
	TOTAL_LENGTH,
	/** 허리 단면 */
	WAIST_WIDTH,
	/** 허벅지 단면 */
	THIGH_WIDTH,
	/** 밑위 */
	RISE,
	/** 인심 */
	INSEAM,
	/** 밑단 */
	HEM_WIDTH,
	/** 엉덩이 단면 */
	HIP_WIDTH;

}
