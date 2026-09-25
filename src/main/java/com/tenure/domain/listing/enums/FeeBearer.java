package com.tenure.domain.listing.enums;

/**
 * 판매 전환 화면 '수수료 부담' 선택. 레코드 사용자만 3종 중 고를 수 있고 기본 사용자는 SELLER 고정. SPLIT 은 확정 사양에서 추가된 값이라 피그마 화면에는 없다
 */
public enum FeeBearer {

	/** 판매자 부담 */
	SELLER,
	/** 구매자 부담 */
	BUYER,
	/** 반반 부담 */
	SPLIT;

}
