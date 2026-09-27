package com.tenure.domain.user.dto.response;

import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.enums.UserGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 내 프로필 + 카운터.
 *
 * <p>카운터는 4종이다 — 피드·아이템·위시·팔로워. 타인 프로필은 위시가 빠진 3종이다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "내 프로필")
public class MyProfileResponse {

	@Schema(description = "사용자 id")
	private final Long id;

	@Schema(description = "닉네임", example = "Sujun")
	private final String nickname;

	@Schema(description = "프로필 사진 URL. 등록하지 않았으면 null")
	private final String profileImageUrl;

	/** 화면에는 없지만 프로필 수정 폼의 초기값으로 필요해 본인 응답에만 싣는다 (DEC-101). */
	@Schema(description = "성별")
	private final Gender gender;

	@Schema(description = "키(cm)", example = "168")
	private final Integer heightCm;

	@Schema(description = "몸무게(kg)", example = "58")
	private final Integer weightKg;

	@Schema(description = "등급")
	private final UserGrade grade;

	@Schema(description = "레코드 보장 만료 시각")
	private final LocalDateTime gradeExpiresAt;

	@Schema(description = "팔로워 수", example = "201")
	private final Integer followerCount;

	@Schema(description = "게시물 수", example = "60")
	private final Integer postCount;

	@Schema(description = "등록한 아이템 수", example = "16")
	private final Integer itemCount;

	@Schema(description = "관심 등록 수", example = "4")
	private final Integer wishCount;
}
