package com.tenure.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "채팅 상대")
public class ChatCounterpartResponse {

	@Schema(description = "닉네임", example = "yujin_closet")
	private final String nickname;

	@Schema(description = "프로필 사진 URL. 없으면 null")
	private final String profileImageUrl;
}
