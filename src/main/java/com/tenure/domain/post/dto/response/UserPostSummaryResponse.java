package com.tenure.domain.post.dto.response;

import com.tenure.domain.post.enums.AspectRatio;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 타인 프로필 그리드의 한 칸.
 *
 * 마이페이지 그리드와 구성이 같다 — 월 헤더로 묶이고 카드마다 하트·북마크 오버레이가 붙는다.
 * 남의 게시물에도 내가 좋아요·저장할 수 있으므로 그 상태를 함께 내려준다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "타인 게시물 카드")
public class UserPostSummaryResponse {

	@Schema(description = "게시물 id")
	private final Long id;

	@Schema(description = "대표 사진 URL")
	private final String imageUrl;

	@Schema(description = "촬영 비율. 카드 높이 계산에 쓴다", example = "RATIO_3_4")
	private final AspectRatio aspectRatio;

	@Schema(description = "착용 날짜. 월 헤더 그룹 기준", example = "2026-06-21")
	private final LocalDate wornDate;

	@Schema(description = "내가 좋아요했으면 true. 그리드 오버레이 아이콘")
	private final Boolean isLiked;

	@Schema(description = "내가 저장했으면 true")
	private final Boolean isSaved;
}
