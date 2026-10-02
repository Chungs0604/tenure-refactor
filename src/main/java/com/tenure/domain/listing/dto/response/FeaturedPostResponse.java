package com.tenure.domain.listing.dto.response;

import com.tenure.domain.post.enums.AspectRatio;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 대표 OOTD 한 칸.
 *
 * aspectRatio 를 싣는 이유는 전체 보기(2825:61147)가 2열 masonry 라 카드 높이가 제각각이어서다.
 * 피드·아이템 히스토리와 같은 이유로 클라이언트가 높이를 계산한다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "대표 OOTD")
public class FeaturedPostResponse {

	@Schema(description = "게시물 id", example = "1")
	private final Long postId;

	@Schema(description = "대표 사진 URL", example = "https://cdn.tenure.app/post/2026/06/ab12.jpg")
	private final String imageUrl;

	@Schema(description = "masonry 높이 계산", example = "RATIO_3_4")
	private final AspectRatio aspectRatio;
}
