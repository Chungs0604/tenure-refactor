package com.tenure.domain.post.dto.request;

import com.tenure.domain.post.enums.AspectRatio;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * OOTD 게시 요청.
 *
 * 사진은 키로 받는다 — 파일은 업로드 API 로 먼저 올린다. 생성 트랜잭션 안에서 파일을 받으면
 * 커넥션을 전송 시간만큼 잡는다.
 *
 * 태그 개수 상한(사진당 5개)은 여기서 막지 않고 Service 에서 판정한다 — 화면이 구분해야 하는
 * 실패라 POST_400_TAG_LIMIT 로 돌려줘야 하고, Bean Validation 은 COMMON_400 이 된다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "OOTD 게시 요청")
public class PostCreateRequest {

	@Schema(description = "착용 날짜. 달력에서 고른 값", example = "2026-09-28")
	@NotNull
	@PastOrPresent
	private LocalDate wornDate;

	@Schema(description = "촬영 비율", example = "RATIO_3_4")
	@NotNull
	private AspectRatio aspectRatio;

	@Schema(description = "바로 게시 여부. 비우면 false", example = "false")
	private Boolean quickPosted;

	@Schema(description = "사진 목록. 1장 이상")
	@NotEmpty
	@Valid
	private List<PostImageRequest> images;

	/** 사진 한 장과 그 위에 붙은 태그들. */
	@Getter
	@Builder
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	@Schema(description = "게시물 사진")
	public static class PostImageRequest {

		@Schema(description = "업로드 API 가 돌려준 저장소 키",
				example = "post/2026/09/0f9b1c2d3e4f4a5b8c7d6e5f4a3b2c1d.jpg")
		@NotBlank
		@Size(max = 500)
		private String imageKey;

		@Schema(description = "노출 순서. 0부터", example = "0")
		@NotNull
		@PositiveOrZero
		private Integer sortOrder;

		@Schema(description = "대표 사진 여부. 한 장만 true. 아무것도 지정하지 않으면 첫 장이 대표가 된다")
		private Boolean isCover;

		@Schema(description = "이 사진에 붙은 태그. 없으면 비운다")
		@Valid
		private List<PostTagRequest> tags;
	}

	/** 사진 속 아이템 태그. 좌표는 이미지 크기 대비 비율이다. */
	@Getter
	@Builder
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	@Schema(description = "아이템 태그")
	public static class PostTagRequest {

		@Schema(description = "태그할 아이템 id. 본인이 보유한 아이템만 된다", example = "1")
		@NotNull
		private Long itemId;

		@Schema(description = "가로 위치. 이미지 폭 대비 비율", example = "0.4200")
		@NotNull
		@DecimalMin("0.0")
		@DecimalMax("1.0")
		private BigDecimal posX;

		@Schema(description = "세로 위치. 이미지 높이 대비 비율", example = "0.6100")
		@NotNull
		@DecimalMin("0.0")
		@DecimalMax("1.0")
		private BigDecimal posY;
	}
}
