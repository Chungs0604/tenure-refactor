package com.tenure.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 팔로워·팔로잉 목록에 반복 노출되는 사용자 한 명. UserProfileResponse 의 축약판이다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "사용자 요약")
public class UserSummaryResponse {

	@Schema(description = "사용자 id")
	private final Long id;

	@Schema(description = "닉네임. 탈퇴 계정은 '탈퇴한 사용자'", example = "Sujun")
	private final String nickname;

	@Schema(description = "프로필 사진 URL. 탈퇴 계정은 null")
	private final String profileImageUrl;

	@Schema(description = "팔로워 수", example = "201")
	private final Integer followerCount;

	@Schema(description = "게시물 수", example = "60")
	private final Integer postCount;

	/**
	 * 래퍼 타입이라야 {@code getIsFollowing()} 이 생겨 JSON 키가 {@code isFollowing} 으로 나간다
	 * (UserProfileResponse 와 같은 이유).
	 */
	@Schema(description = "내가 이 사용자를 팔로우 중이면 true")
	private final Boolean isFollowing;
}
