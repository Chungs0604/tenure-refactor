package com.tenure.domain.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 회원탈퇴 사유. 화면(3063:29262)의 '골라주세요.' 드롭다운 4종이며 자유 입력이 아니다.
 *
 * <p>'기타(직접 입력)' 은 화면에 없다.
 */
@Getter
@RequiredArgsConstructor
public enum WithdrawalReason {

	LOW_USAGE("사용 빈도가 낮아요."),
	HARD_TO_FIND_ITEM("원하는 아이템을 찾기 어려워요."),
	USING_OTHER_SERVICE("다른 서비스를 이용할 예정이에요."),
	UNSATISFIED_TRADE("거래 경험이 만족스럽지 않았어요.");

	private final String label;
}
