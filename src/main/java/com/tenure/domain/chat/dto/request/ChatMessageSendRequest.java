package com.tenure.domain.chat.dto.request;

import com.tenure.domain.chat.enums.ChatMessageType;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 메시지 전송(WS). TEXT 면 content 필수, IMAGE 면 imageKeys 1장 이상 — 타입별 필수 여부는
 * 어노테이션으로 표현할 수 없어 ChatService 가 확인한다. WS 파이프라인에는 bean validation 이
 * 연결돼 있지 않아 여기 어노테이션을 붙여도 동작하지 않는다 — 검증은 전부 Service 몫이다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessageSendRequest {

	private ChatMessageType messageType;

	private String content;

	private List<String> imageKeys;
}
