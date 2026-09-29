package com.tenure.domain.post.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 상세의 작성자 줄. 화면 문구가 '팔로우 101명 · 피드 60개' 다.
 *
 * 키·몸무게를 싣지 않는다 — 그 줄에 체형이 없다 (DEC-101).
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "상세의 작성자")
public class PostAuthorResponse {

	@Schema(description = "작성자 id. 본인 게시물인지 판별해 더보기 메뉴를 가른다")
	private final Long id;

	@Schema(description = "닉네임", example = "Sujun")
	private final String nickname;

	@Schema(description = "프로필 사진 URL. 없으면 null")
	private final String profileImageUrl;

	@Schema(description = "팔로워 수", example = "101")
	private final Integer followerCount;

	@Schema(description = "게시물 수", example = "60")
	private final Integer postCount;
}
