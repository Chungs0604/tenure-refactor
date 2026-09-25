package com.tenure.domain.report.enums;

/**
 * 상품 상세 6종 + OOTD 더보기 6종의 합집합. '가품 또는 정품 의심' '상품 정보가 실제와 다름' '도용한 사진 또는 권리 침해' '사기 또는 외부 거래 유도' '거래 금지 품목'…
 */
public enum ReportReason {

	COUNTERFEIT,
	INFO_MISMATCH,
	STOLEN_PHOTO,
	FRAUD_EXTERNAL_TRADE,
	PROHIBITED_ITEM,
	INAPPROPRIATE,
	NOT_WORN_OOTD,
	SPAM,
	PRIVACY_EXPOSURE,
	IRRELEVANT;

}
