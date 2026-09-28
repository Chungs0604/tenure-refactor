package com.tenure.domain.user.dto.request;

import com.tenure.domain.user.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로필 수정 요청.
 *
 * <p>사진만 선택이다. JSON 은 '생략'과 {@code null} 을 구분하지 못하므로 나머지를 필수로 두어
 * {@code null} 의 의미를 {@code profileImageKey} 한 곳에만 남긴다 — 보낸 값으로 덮어쓴다는
 * 규칙이 하나로 유지된다. 수정 화면이 폼 값을 항상 함께 보내므로 화면과도 맞는다.
 *
 * <p>길이 제약은 컬럼 길이와 맞춘다. 안 막으면 DB 에서 끊겨 500 이 된다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "프로필 수정 요청")
public class UserUpdateRequest {

	@Schema(description = "닉네임", example = "Sujun")
	@NotBlank
	@Size(max = 30)
	private String nickname;

	@Schema(description = "프로필 사진 저장소 키. null 이면 기본 이미지로 되돌린다")
	@Size(max = 500)
	private String profileImageKey;

	@Schema(description = "성별")
	@NotNull
	private Gender gender;

	@Schema(description = "키(cm)", example = "170")
	@NotNull
	@Min(0)
	@Max(200)
	private Integer heightCm;

	@Schema(description = "몸무게(kg)", example = "64")
	@NotNull
	@Min(0)
	@Max(150)
	private Integer weightKg;
}
