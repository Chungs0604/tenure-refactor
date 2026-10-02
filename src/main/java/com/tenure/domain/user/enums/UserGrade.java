package com.tenure.domain.user.enums;

/**
 * 마이페이지 '레코드 사용자 <-> 기본 사용자'. 판매 전환 화면이 등급별로 2종('다 작성된 판매 전환 페이지 - 레코드 사용자 입장' / '- 기본 사용자 입장')
 */
public enum UserGrade {

	BASIC,
	RECORD;

	/**
	 * 기본 사용자인지. 판매 전환에서 수수료 부담·배송비를 고를 수 있는지가 이 값으로 갈린다
	 * (3342:33693) — 기본 사용자는 판매자 부담·0원 고정이다.
	 */
	public boolean isBasic() {
		return this == BASIC;
	}

}
