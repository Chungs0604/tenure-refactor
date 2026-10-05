package com.tenure.domain.chat.dto.response;

import com.tenure.domain.chat.enums.ChatCtaAction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 헤더 버튼 하나. 좌측(아이템 이동)·우측(거래 단계) 두 개가 항상 내려간다 (design-audit.md G-2) */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "채팅방 헤더 CTA")
public class ChatCtaResponse {

	@Schema(description = "버튼 문구", example = "구매하기")
	private final String label;

	@Schema(description = "이동 대상 화면")
	private final ChatCtaAction action;

	@Schema(description = "이동 대상 id — 아이템·제안·거래 중 하나", example = "1")
	private final Long targetId;

	@Schema(description = "비활성 여부. true 면 버튼은 보이지만 눌러도 동작하지 않는다", example = "true")
	private final Boolean enabled;
}
