package com.tenure.domain.post.dto.response;

import com.tenure.domain.post.enums.AspectRatio;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 홈 피드의 카드.
 *
 * 카드에는 사진과 하트·북마크 아이콘뿐이다. 작성자도 좋아요 수도 그리지 않으므로 싣지 않는다 —
 * 작성자는 카드를 눌러 들어간 상세에서 보여준다.
 *
 * 사진은 대표 1장만 싣는다. 비율이 제각각인 masonry 그리드라 클라이언트가 높이를 계산하려면
 * aspectRatio 가 필요하다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "피드 카드")
public class PostSummaryResponse {

	@Schema(description = "게시물 id")
	private final Long id;

	@Schema(description = "대표 사진 URL")
	private final String imageUrl;

	@Schema(description = "촬영 비율. 카드 높이 계산에 쓴다", example = "RATIO_3_4")
	private final AspectRatio aspectRatio;

	@Schema(description = "내가 좋아요했으면 true")
	private final Boolean isLiked;

	@Schema(description = "내가 저장했으면 true")
	private final Boolean isSaved;
}
