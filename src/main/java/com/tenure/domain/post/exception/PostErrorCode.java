package com.tenure.domain.post.exception;

import com.tenure.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정본: docs/api-spec.md §5. */
@Getter
@RequiredArgsConstructor
public enum PostErrorCode implements ErrorCode {

	POST_404(HttpStatus.NOT_FOUND, "POST_404", "게시물을 찾을 수 없습니다."),
	POST_404_IMAGE(HttpStatus.NOT_FOUND, "POST_404_IMAGE", "사진을 찾을 수 없습니다."),
	POST_403(HttpStatus.FORBIDDEN, "POST_403", "본인의 게시물이 아닙니다."),
	POST_400_TAG_LIMIT(HttpStatus.BAD_REQUEST, "POST_400_TAG_LIMIT", "태그는 사진당 최대 5개까지 가능합니다."),
	POST_400_TAG_OWNERSHIP(HttpStatus.BAD_REQUEST, "POST_400_TAG_OWNERSHIP", "본인이 보유한 아이템만 태그할 수 있습니다."),
	POST_400_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "POST_400_IMAGE_REQUIRED", "사진을 1장 이상 등록해주세요."),
	POST_409_FEATURED(HttpStatus.CONFLICT, "POST_409_FEATURED", "판매 중인 상품의 대표 OOTD 는 삭제할 수 없습니다."),
	POST_400_EDIT_WINDOW(HttpStatus.BAD_REQUEST, "POST_400_EDIT_WINDOW", "바로 게시한 지 3일이 지난 게시물은 태그를 수정할 수 없습니다."),
	POST_409_LIKE(HttpStatus.CONFLICT, "POST_409_LIKE", "이미 좋아요한 게시물입니다."),
	POST_409_SAVE(HttpStatus.CONFLICT, "POST_409_SAVE", "이미 저장한 게시물입니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
