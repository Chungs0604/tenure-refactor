package com.tenure.domain.post.dto.response;

import com.tenure.domain.post.enums.AspectRatio;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 마이페이지 그리드의 한 칸.
 *
 * wornDate 를 싣는 이유는 화면이 '6월'·'5월' 월 헤더로 묶기 때문이다 — 클라이언트가
 * 이 값으로 그룹을 만든다. 그리드도 피드와 같은 masonry 라 aspectRatio 가 필요하다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "내 게시물 카드")
public class MyPostSummaryResponse {

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
