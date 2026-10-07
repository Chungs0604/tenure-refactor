package com.tenure.domain.notification.dto.response;

import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.domain.notification.enums.NotificationTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "알림 목록 행")
public class NotificationResponse {

	@Schema(description = "알림 id", example = "1")
	private final Long id;

	@Schema(description = "탭", example = "TRADE_STATUS")
	private final NotificationCategory category;

	@Schema(description = "행 썸네일. 없으면 null")
	private final String imageUrl;

	@Schema(description = "제목 — 아이템명", example = "Levis / LVC 1955 501")
	private final String title;

	@Schema(description = "본문 — 사건", example = "가격이 128,000원으로 변경됐어요.")
	private final String body;

	@Schema(description = "딥링크 대상 종류")
	private final NotificationTargetType targetType;

	@Schema(description = "딥링크 대상 id", example = "1")
	private final Long targetId;

	@Schema(description = "true 면 대상이 삭제됨 — '삭제된 게시물입니다' 표시", example = "false")
	private final Boolean targetDeleted;

	@Schema(description = "읽은 시각. null 이면 미읽음")
	private final LocalDateTime readAt;

	@Schema(description = "발행 시각")
	private final LocalDateTime createdAt;
}
