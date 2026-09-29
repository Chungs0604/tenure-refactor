package com.tenure.global.storage.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 업로드 결과. 키와 URL 을 함께 준다.
 *
 * 이후 생성 API 에는 key 만 넘긴다 — 컬럼에 전체 URL 을 넣으면 저장소나 도메인이
 * 바뀔 때 전수 마이그레이션이 필요하다 (DEC-006). url 은 업로드 직후 미리보기용이다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "이미지 업로드 결과")
public class ImageUploadResponse {

	@Schema(description = "저장소 키. 생성·수정 API 에 이 값을 넘긴다",
			example = "post/2026/09/0f9b1c2d3e4f4a5b8c7d6e5f4a3b2c1d.jpg")
	private final String key;

	@Schema(description = "미리보기 URL", example = "/uploads/post/2026/09/0f9b1c2d.jpg")
	private final String url;
}
