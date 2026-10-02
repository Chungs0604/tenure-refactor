package com.tenure.domain.listing.exception;

import com.tenure.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum ListingErrorCode implements ErrorCode {

	LISTING_404(HttpStatus.NOT_FOUND, "LISTING_404", "판매 상품을 찾을 수 없습니다."),
	LISTING_403(HttpStatus.FORBIDDEN, "LISTING_403", "본인의 판매 상품이 아닙니다."),
	LISTING_400_FEATURED_RANGE(HttpStatus.BAD_REQUEST, "LISTING_400_FEATURED_RANGE", "대표 OOTD 는 1개 이상 5개 이하로 선택해주세요."),
	LISTING_403_FEE_BEARER(HttpStatus.FORBIDDEN, "LISTING_403_FEE_BEARER", "기본 사용자는 수수료 부담과 배송비를 지정할 수 없습니다."),
	LISTING_409_SOLD(HttpStatus.CONFLICT, "LISTING_409_SOLD", "이미 판매 완료된 상품입니다."),
	LISTING_409_ALREADY_OPEN(HttpStatus.CONFLICT, "LISTING_409_ALREADY_OPEN", "이미 판매 중인 아이템입니다."),
	LISTING_409_CLOSED(HttpStatus.CONFLICT, "LISTING_409_CLOSED", "판매가 끝난 등록은 수정할 수 없습니다."),
	LISTING_409_IN_TRADE(HttpStatus.CONFLICT, "LISTING_409_IN_TRADE", "진행 중인 거래가 있어 판매 상태를 바꿀 수 없습니다."),
	LISTING_400_STATUS(HttpStatus.BAD_REQUEST, "LISTING_400_STATUS", "판매중으로 되돌릴 수 없습니다. 판매 전환을 다시 진행해주세요.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
