package com.tenure.global.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 목록 응답. {@link BaseResponse} 의 data 에 담긴다.
 *
 * <p>Spring 의 {@code Page} 를 그대로 내리지 않는다 — 직렬화 형태가 버전에 따라 바뀌고
 * 클라이언트가 쓰지 않는 필드(pageable, sort 객체)가 통째로 따라 나간다.
 *
 * @param page 0부터 시작한다
 */
@Schema(description = "페이지 응답")
public record PageResponse<T>(
		@Schema(description = "현재 페이지 (0부터)", example = "0")
		int page,
		@Schema(description = "페이지 크기", example = "20")
		int size,
		@Schema(description = "전체 건수", example = "137")
		long totalElements,
		@Schema(description = "전체 페이지 수", example = "7")
		int totalPages,
		@Schema(description = "다음 페이지 존재 여부", example = "true")
		boolean hasNext,
		@Schema(description = "목록") List<T>
		content
) {
	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.hasNext(),
				page.getContent());
	}
}
