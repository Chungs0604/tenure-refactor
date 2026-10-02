package com.tenure.domain.category.enums;

import static com.tenure.domain.category.enums.MeasurementField.CHEST_WIDTH;
import static com.tenure.domain.category.enums.MeasurementField.HEM_WIDTH;
import static com.tenure.domain.category.enums.MeasurementField.HIP_WIDTH;
import static com.tenure.domain.category.enums.MeasurementField.INSEAM;
import static com.tenure.domain.category.enums.MeasurementField.RISE;
import static com.tenure.domain.category.enums.MeasurementField.SHOULDER_WIDTH;
import static com.tenure.domain.category.enums.MeasurementField.SLEEVE_LENGTH;
import static com.tenure.domain.category.enums.MeasurementField.THIGH_WIDTH;
import static com.tenure.domain.category.enums.MeasurementField.TOTAL_LENGTH;
import static com.tenure.domain.category.enums.MeasurementField.WAIST_WIDTH;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 대분류가 받는 실측 항목 세트. category.measurement_scheme 이 이 값을 갖는다.
 *
 * size_scheme 으로 대신할 수 없어 따로 둔다 — 하의와 치마가 둘 다 BOTTOM 인데 실측이 다르다.
 * item_measurement 는 10개 고정 필드이고, 세트마다 '어떤 걸 채우느냐' 만 달라진다.
 * 세트에 없는 항목에 값이 오거나 세트의 항목이 비면 400 이다 — 신발에 어깨 너비가 박히면
 * 상세가 엉뚱한 실측을 그린다.
 */
public enum MeasurementScheme {

	/** 상의 · 아우터 · 원피스 */
	TOP(EnumSet.of(SHOULDER_WIDTH, CHEST_WIDTH, SLEEVE_LENGTH, TOTAL_LENGTH)),

	/** 하의 */
	BOTTOM(EnumSet.of(WAIST_WIDTH, THIGH_WIDTH, TOTAL_LENGTH, RISE, INSEAM, HEM_WIDTH)),

	/** 치마 */
	SKIRT(EnumSet.of(WAIST_WIDTH, HIP_WIDTH, HEM_WIDTH, TOTAL_LENGTH)),

	/** 신발 · 가방 · 모자 · 액세서리 · 주얼리. 실측 입력 자체가 없다 */
	NONE(EnumSet.noneOf(MeasurementField.class));

	private final Set<MeasurementField> fields;

	MeasurementScheme(Set<MeasurementField> fields) {
		this.fields = Collections.unmodifiableSet(fields);
	}

	/** API-090 이 그대로 내려준다 — 프론트가 이 목록대로 입력란을 그린다. */
	public Set<MeasurementField> getFields() {
		return fields;
	}

	public boolean accepts(MeasurementField field) {
		return fields.contains(field);
	}

	/** 실측을 아예 받지 않는 세트인지. 행을 만들지 말아야 한다 */
	public boolean isNone() {
		return this == NONE;
	}
}
