package com.tenure.domain.item.exception;

import com.tenure.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum ItemErrorCode implements ErrorCode {

	ITEM_404(HttpStatus.NOT_FOUND, "ITEM_404", "아이템을 찾을 수 없습니다."),
	ITEM_403(HttpStatus.FORBIDDEN, "ITEM_403", "본인의 아이템이 아닙니다."),
	ITEM_409_ON_SALE(HttpStatus.CONFLICT, "ITEM_409_ON_SALE", "판매 중이거나 거래가 진행 중이라 삭제할 수 없습니다."),
	ITEM_400_MEASUREMENT(HttpStatus.BAD_REQUEST, "ITEM_400_MEASUREMENT", "해당 카테고리에 맞지 않는 실측 항목입니다."),
	ITEM_400_CATEGORY(HttpStatus.BAD_REQUEST, "ITEM_400_CATEGORY", "세부 카테고리를 선택해주세요."),
	ITEM_409_TAG_CLOSED_PERIOD(HttpStatus.CONFLICT, "ITEM_409_TAG_CLOSED_PERIOD", "이미 판매된 아이템의 과거 태그는 수정할 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
