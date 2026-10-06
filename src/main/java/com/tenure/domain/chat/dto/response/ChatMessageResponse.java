package com.tenure.domain.chat.dto.response;

import com.tenure.domain.chat.enums.ChatMessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 메시지 한 건. REST 히스토리 조회와 WS 실시간 브로드캐스트가 같은 형태를 씀.
 *
 * content·images 는 messageType 에 따라 둘 중 하나만 채워짐 — 한 말풍선에 여러 장이
 * 묶이는 이미지 메시지는 content 하나로 담을 수 없어 images 를 따로 둠.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "채팅 메시지")
public class ChatMessageResponse {

	@Schema(description = "메시지 id", example = "1")
	private final Long id;

	@Schema(description = "채팅방 id. WS 브로드캐스트에서 클라이언트가 방을 식별하는 데 씀", example = "1")
	private final Long roomId;

	@Schema(description = "발신자 id", example = "1")
	private final Long senderId;

	@Schema(description = "메시지 종류", example = "TEXT")
	private final ChatMessageType messageType;

	@Schema(description = "본문. TEXT 일 때만 값이 있다")
	private final String content;

	@Schema(description = "사진 URL 목록. IMAGE 일 때만 값이 있다 — 여러 장이 한 말풍선에 묶인다")
	private final List<String> images;

	@Schema(description = "전송 시각", example = "2026-06-21T18:30:00")
	private final LocalDateTime createdAt;
}
