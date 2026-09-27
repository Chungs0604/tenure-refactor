package com.tenure.domain.user.dto.response;

import com.tenure.domain.user.enums.UserGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 타인 프로필.
 *
 * <p>헤더 2행이 {@code 레코드 사용자 | 168cm · 58kg} 라 등급과 체형을 함께 싣고 성별은 뺀다.
 * 카운터도 3종뿐이다 — 남의 위시리스트는 공개하지 않는다 (DEC-101).
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "타인 프로필")
public class UserProfileResponse {

	@Schema(description = "사용자 id")
	private final Long id;

	@Schema(description = "닉네임. 탈퇴 계정은 '탈퇴한 사용자'", example = "Sujun")
	private final String nickname;

	@Schema(description = "프로필 사진 URL. 탈퇴 계정은 null")
	private final String profileImageUrl;

	@Schema(description = "등급. 헤더의 '레코드 사용자' 배지")
	private final UserGrade grade;

	@Schema(description = "키(cm). 탈퇴 계정은 null", example = "168")
	private final Integer heightCm;

	@Schema(description = "몸무게(kg). 탈퇴 계정은 null", example = "58")
	private final Integer weightKg;

	@Schema(description = "게시물 수", example = "60")
	private final Integer postCount;

	@Schema(description = "등록한 아이템 수", example = "16")
	private final Integer itemCount;

	@Schema(description = "팔로워 수", example = "201")
	private final Integer followerCount;

	/**
	 * 래퍼 타입이라야 {@code getIsFollowing()} 이 생겨 JSON 키가 {@code isFollowing} 으로 나간다.
	 * {@code boolean} 이면 getter 가 {@code isFollowing()} 이고 Jackson 이 {@code following} 으로
	 * 내려서 명세와 어긋난다.
	 */
	@Schema(description = "내가 팔로우 중이면 true. 팔로우 버튼 상태")
	private final Boolean isFollowing;

	@Schema(description = "내가 차단했으면 true. 차단하기/차단해제 토글 상태")
	private final Boolean isBlocked;
}
