package com.tenure.domain.category.enums;

/**
 * 사이즈 칩이 속한 체계. 한 세트 안에서 칩을 줄로 묶는 근거다.
 *
 * 화면은 체계 이름을 글자로 쓰지 않고 줄바꿈으로만 구분한다 — 그래서 표시용이 아니라
 * 정렬과 뜻풀이용 값이다. 나중에 'EU 42' 처럼 체계를 함께 보여주기로 하면 이 값을 쓴다.
 */
public enum SizeSystem {

	/** XS · S · M · L · XL · 2XL · 3XL */
	ALPHA,

	/** 85 · 90 · 95 · 100 · 105 · 110 · 115 */
	KR_NUMERIC,

	/** 36 · 38 · … · 50 */
	EU_NUMERIC,

	/** 24 · 25 · … · 40. 하의 · 치마 */
	INCH,

	/** 210 · 215 · … · 310. 신발 */
	MM,

	/** 0 · 1 · … · 6 */
	FREE_NUMBER,

	/** 'One size' 한 칩 */
	ONE_SIZE;

}
