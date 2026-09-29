package com.tenure.domain.post.dto.response;

import com.tenure.domain.post.enums.AspectRatio;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * OOTD 상세.
 *
 * isLiked·isSaved·isFollowing 이 있어야 하트·북마크·팔로우 버튼의
 * 초기 상태를 그릴 수 있다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "OOTD 상세")
public class PostResponse {

	@Schema(description = "게시물 id")
	private final Long id;

	@Schema(description = "착용 날짜", example = "2026-06-21")
	private final LocalDate wornDate;

	@Schema(description = "촬영 비율", example = "RATIO_3_4")
	private final AspectRatio aspectRatio;

	@Schema(description = "사진 목록. 올린 순서대로")
	private final List<PostImageResponse> images;

	@Schema(description = "작성자")
	private final PostAuthorResponse author;

	@Schema(description = "작성자를 팔로우 중이면 true. 본인 게시물이면 false")
	private final Boolean isFollowing;

	@Schema(description = "내가 좋아요했으면 true")
	private final Boolean isLiked;

	@Schema(description = "내가 저장했으면 true")
	private final Boolean isSaved;

	@Schema(description = "좋아요 수", example = "1234")
	private final Integer likeCount;

	@Schema(description = "저장 수", example = "1234")
	private final Integer saveCount;
}
