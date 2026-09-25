package com.tenure.domain.offer.exception;

import com.tenure.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum OfferErrorCode implements ErrorCode {

	OFFER_404(HttpStatus.NOT_FOUND, "OFFER_404", "제안을 찾을 수 없습니다."),
	OFFER_403(HttpStatus.FORBIDDEN, "OFFER_403", "본인과 관련된 제안이 아닙니다."),
	OFFER_409_ALREADY_USED(HttpStatus.CONFLICT, "OFFER_409_ALREADY_USED", "이 아이템에는 이미 구매 의사를 보냈습니다."),
	OFFER_400_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "OFFER_400_NOT_ALLOWED", "구매 제안을 받지 않는 상품입니다."),
	OFFER_403_BLOCKED(HttpStatus.FORBIDDEN, "OFFER_403_BLOCKED", "차단 관계인 사용자에게는 제안할 수 없습니다."),
	OFFER_400_SELF(HttpStatus.BAD_REQUEST, "OFFER_400_SELF", "본인의 상품에는 제안할 수 없습니다."),
	OFFER_409_NOT_PENDING(HttpStatus.CONFLICT, "OFFER_409_NOT_PENDING", "이미 응답했거나 만료된 제안입니다."),
	OFFER_410_EXPIRED(HttpStatus.GONE, "OFFER_410_EXPIRED", "제안 유효 시간이 지났습니다."),
	OFFER_400_ADDRESS_REQUIRED(HttpStatus.BAD_REQUEST, "OFFER_400_ADDRESS_REQUIRED", "배송지를 먼저 등록해주세요.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
