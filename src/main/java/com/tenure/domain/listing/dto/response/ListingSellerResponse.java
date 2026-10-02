package com.tenure.domain.listing.dto.response;

import com.tenure.domain.user.enums.UserGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * '판매자 정보' 섹션. 판매 이력이 없는 아이템은 섹션 자체가 없어 null 이다.
 *
 * 등급을 싣는 이유는 화면이 '팔로워 10 | 레코드 사용자' 로 그리기 때문이다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "판매자 정보")
public class ListingSellerResponse {

	@Schema(description = "사용자 id", example = "1")
	private final Long id;

	@Schema(description = "닉네임", example = "GilDong")
	private final String nickname;

	@Schema(description = "프로필 사진 URL", example = "https://cdn.tenure.app/user/2026/06/ab12.jpg")
	private final String profileImageUrl;

	@Schema(description = "팔로워 수", example = "10")
	private final Integer followerCount;

	@Schema(description = "등급. 수수료·배송비 표기가 갈린다", example = "RECORD")
	private final UserGrade grade;
}
