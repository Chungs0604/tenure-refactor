package com.tenure.domain.post.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시물 수정 요청. 바꿀 수 있는 것은 태그뿐이다.
 *
 * 사진과 착용일은 받지 않는다 — 수정 화면(SCR-019)이 태그만 다시 붙이게 되어 있고, 사진을
 * 바꾸면 이미 적립된 착용 기록의 근거가 사라진다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "게시물 태그 수정 요청")
public class PostUpdateRequest {

	@Schema(description = "수정할 태그 목록")
	@NotEmpty
	@Valid
	private List<PostTagEditRequest> tags;

	/** 태그 한 건의 변경. */
	@Getter
	@Builder
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	@Schema(description = "태그 변경")
	public static class PostTagEditRequest {

		@Schema(description = "수정할 태그 id. 상세 응답의 images[].tags[].postItemTagId", example = "1")
		@NotNull
		private Long postItemTagId;

		@Schema(description = "바꿀 아이템 id. 비우면 태그를 지운다", example = "2")
		private Long itemId;
	}
}
