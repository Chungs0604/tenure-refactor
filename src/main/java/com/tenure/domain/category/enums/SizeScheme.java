package com.tenure.domain.category.enums;

/**
 * 대분류가 쓰는 사이즈 칩 세트. category.size_scheme 이 이 값을 갖고 size_option 이 칩을 갖는다.
 *
 * 상세 수정 바텀시트 10종을 열어 확정했다 — 카테고리마다 펼쳐지는 칩이 다르다.
 * 상의처럼 한 세트가 여러 체계를 섞어 쓰므로 체계 이름이 아니라 세트 이름이다.
 */
public enum SizeScheme {

	/** 상의 · 아우터 · 원피스. ALPHA + KR_NUMERIC + EU_NUMERIC + FREE_NUMBER */
	TOP,

	/** 하의 · 치마. ALPHA + INCH + FREE_NUMBER */
	BOTTOM,

	/** 신발. MM 210~310 */
	SHOE,

	/** 모자 · 가방 · 액세서리 · 쥬얼리. 칩이 'One size' 하나뿐이다 */
	ONE_SIZE;

}
