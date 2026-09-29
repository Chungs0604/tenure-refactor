package com.tenure.domain.item.dto.response;

import com.tenure.domain.post.enums.AspectRatio;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 아이템 히스토리 그리드의 사진 한 장.
 *
 * aspectRatio 를 싣는 이유는 그리드가 masonry 라서다 — 카드 높이가 제각각이라
 * 클라이언트가 비율로 계산한다. 피드와 같은 규칙이다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "히스토리 사진")
public class ItemHistoryPostResponse {

	@Schema(description = "게시물 id", example = "1")
	private final Long postId;

	@Schema(description = "대표 사진 URL")
	private final String imageUrl;

	@Schema(description = "촬영 비율", example = "RATIO_3_4")
	private final AspectRatio aspectRatio;

	@Schema(description = "착용 날짜", example = "2026-05-26")
	private final LocalDate wornDate;
}
