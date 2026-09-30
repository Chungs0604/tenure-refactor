package com.tenure.domain.item.enums;

/**
 * 태그된 아이템 시트 아래에 붙는 추천 블록.
 *
 * 유사도 모델을 쓰지 않는다 — AI 아이템 인식을 범위 밖으로 둔 것과 같은 기준이며,
 * 블록마다 카테고리와 공동 태그로 최근 게시물을 모은다.
 *
 * 제목과 부제를 서버가 만들어 내려준다. 화면에 그대로 쓰는 문구라 고칠 때 앱을 새로
 * 배포하지 않아도 되고, 기준 아이템 이름과 카테고리가 문장에 들어가 클라이언트가 조립하면
 * 한글 조사까지 따져야 한다.
 */
public enum RecommendationBlock {

	/** 비슷한 무드 모아보기. 대분류가 같은 다른 아이템이 태그된 최근 게시물 */
	SIMILAR_MOOD,

	/** {브랜드} / {아이템명}과 비슷한 스타일. 세부 카테고리가 같은 다른 아이템이 태그된 최근 게시물 */
	SIMILAR_ITEM,

	/** 함께 참고하기 좋은 코디. 이 아이템과 같은 게시물에 태그된 아이템들이 등장하는 최근 게시물 */
	GOOD_COORDI;

	/**
	 * 화면에 그대로 쓰는 제목.
	 *
	 * SIMILAR_ITEM 만 기준 아이템 이름이 들어간다 — 화면이 'Levis / LVC 1955 501과 비슷한 스타일',
	 * 'Guidi / 788z Boots와 비슷한 스타일' 로 받침에 따라 조사를 바꾼다.
	 */
	public String titleOf(String brandName, String itemName) {
		return switch (this) {
			case SIMILAR_MOOD -> "비슷한 무드 모아보기";
			case SIMILAR_ITEM -> "%s / %s%s 비슷한 스타일"
					.formatted(brandName, itemName, hasFinalConsonant(itemName) ? "과" : "와");
			case GOOD_COORDI -> "함께 참고하기 좋은 코디";
		};
	}

	/**
	 * 화면에 그대로 쓰는 부제.
	 *
	 * SIMILAR_ITEM 은 아이템마다 다르다 — '비슷한 데님 스타일을 모아봤어요.' /
	 * '비슷한 부츠 스타일을 모아봤어요.' 처럼 그 아이템의 세부 카테고리가 들어간다.
	 */
	public String descriptionOf(String categoryName) {
		return switch (this) {
			case SIMILAR_MOOD -> "지금 본 착장과 어울리는 스타일을 추천해요.";
			case SIMILAR_ITEM -> "비슷한 %s 스타일을 모아봤어요.".formatted(categoryName);
			case GOOD_COORDI -> "지금 스타일과 이어서 보기 좋은 코디예요.";
		};
	}

	/**
	 * 숫자를 읽었을 때 받침이 있는 것 — 일(1) 삼(3) 육(6) 칠(7) 팔(8).
	 *
	 * 0 도 넣는다. 자리에 따라 십·백·천·영 중 무엇으로 읽히든 전부 받침이 있다.
	 */
	private static final String DIGITS_WITH_FINAL_CONSONANT = "013678";

	/**
	 * 마지막 글자에 받침이 있는지. 조사 '과/와' 를 가르는 판정이다.
	 *
	 * 숫자는 소리 내어 읽은 결과를 본다 — 화면의 'LVC 1955 501과' 가 그 경우로, 1 을 '일' 로
	 * 읽어 ㄹ 받침이 생긴다. 영문은 단어마다 읽는 법이 달라 사전 없이는 알 수 없으므로 받침이
	 * 없는 것으로 본다 — 화면의 '788z Boots와' 가 그렇게 읽힌다.
	 */
	private static boolean hasFinalConsonant(String word) {
		if (word == null || word.isBlank()) {
			return false;
		}

		char last = word.charAt(word.length() - 1);

		if (last >= '가' && last <= '힣') {
			return (last - '가') % 28 != 0;
		}

		return DIGITS_WITH_FINAL_CONSONANT.indexOf(last) >= 0;
	}
}
