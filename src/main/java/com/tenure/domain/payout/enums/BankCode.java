package com.tenure.domain.payout.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 정산 계좌의 은행. 화면은 텍스트 입력이지만 자유 입력을 받으면 같은 은행이 '토스', '토스뱅크',
 * '토스 뱅크' 로 쌓여 나중에 송금 연동을 붙일 때 정리할 수 없다. 드롭다운으로 확정했다.
 *
 * <p>국내 주요 은행 20종이다. {@code code} 는 금융결제원 기관코드로, 지금은 쓰이지 않고
 * 송금·계좌 실명조회 연동을 붙일 때 그쪽에 넘길 값이다 — 연동 시점에 해당 PG 의 코드표와
 * 대조해 확인해야 한다.
 *
 * <p>DB 에는 enum 이름을 저장한다(예 {@code TOSS_BANK}). 프로젝트의 다른 enum 과 같은 방식이다.
 */
@Getter
@RequiredArgsConstructor
public enum BankCode {

	KOOKMIN("004", "KB국민은행"),
	SHINHAN("088", "신한은행"),
	HANA("081", "하나은행"),
	WOORI("020", "우리은행"),
	SC_FIRST("023", "SC제일은행"),
	NONGHYUP("011", "NH농협은행"),
	SUHYUP("007", "Suhyup수협"),
	IBK("003", "IBK기업은행"),
	KDB("002", "KDB산업은행"),
	POST_OFFICE("071", "우체국"),
	KAKAO_BANK("090", "카카오뱅크"),
	TOSS_BANK("092", "토스뱅크"),
	K_BANK("089", "케이뱅크"),
	SAEMAUL("045", "새마을금고"),
	SHINHYUP("048", "신협"),
	BUSAN("032", "부산은행"),
	IM("031", "대구(iM)은행"),
	KYONGNAM("039", "경남은행"),
	GWANGJU("034", "광주은행"),
	JEONBUK("037", "전북은행");

	/** 금융결제원 기관코드. 송금 연동 전까지는 저장도 전송도 하지 않는다. */
	private final String code;

	/** 드롭다운과 목록에 노출하는 이름. */
	private final String bankName;
}
