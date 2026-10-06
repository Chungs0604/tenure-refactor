package com.tenure.domain.chat.dto.request;

import com.tenure.domain.chat.enums.ChatMessageType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 메시지 전송(WS). TEXT 면 content 필수, IMAGE 면 imageKeys 1장 이상 — 타입별 필수 여부는
 * 어노테이션으로 표현할 수 없어 ChatService 가 확인한다.
 *
 * <p>spring-messaging 이 jakarta.validation 을 자동으로 붙이므로({@code @Payload @Valid})
 * 여기 어노테이션도 실제로 동작한다 — 개수·길이 상한만 선언적으로 막고, 타입별 필수 여부는
 * 여전히 Service 의 몫이다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessageSendRequest {

	@NotNull
	private ChatMessageType messageType;

	@Size(max = 2000)
	private String content;

	/** 한 말풍선에 너무 많은 사진이 묶이지 않게 거는 상한 — 화면이 보여주는 다건 묶음 기준 */
	@Size(max = 10)
	private List<String> imageKeys;
}
