package com.tenure.domain.item.enums;

/**
 * 태그된 아이템 시트 아래에 붙는 추천 블록.
 *
 * 유사도 모델을 쓰지 않는다 — AI 아이템 인식을 범위 밖으로 둔 것과 같은 기준이며,
 * 블록마다 카테고리와 공동 태그로 최근 게시물을 모은다.
 */
public enum RecommendationBlock {

	/** 비슷한 무드 모아보기. 대분류가 같은 다른 아이템이 태그된 최근 게시물 */
	SIMILAR_MOOD("비슷한 무드 모아보기", "지금 본 옷차림과 어울리는 스타일을 추천해요."),

	/** {브랜드} / {아이템명}과 비슷한 스타일. 세부 카테고리가 같은 다른 아이템이 태그된 최근 게시물 */
	SIMILAR_ITEM(null, "비슷한 디테일의 스타일을 모아봤어요."),

	/** 함께 참고하기 좋은 코디. 이 아이템과 같은 게시물에 태그된 아이템들이 등장하는 최근 게시물 */
	GOOD_COORDI("함께 참고하기 좋은 코디", "지금 스타일과 어울리기 좋은 코디예요.");

	private final String title;
	private final String description;

	RecommendationBlock(String title, String description) {
		this.title = title;
		this.description = description;
	}

	/** SIMILAR_ITEM 은 기준 아이템 이름이 제목에 들어가므로 만들어 준다. */
	public String titleFor(String brandName, String itemName) {
		return title != null ? title : "%s / %s과 비슷한 스타일".formatted(brandName, itemName);
	}

	public String getDescription() {
		return description;
	}
}
