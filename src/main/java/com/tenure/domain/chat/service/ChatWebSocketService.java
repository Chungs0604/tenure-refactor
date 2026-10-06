package com.tenure.domain.chat.service;

import com.tenure.domain.chat.dto.response.ChatMessageResponse;
import com.tenure.domain.chat.dto.response.ChatReadEventResponse;
import com.tenure.domain.chat.dto.response.ChatRoomListUpdateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/*
 * 채팅 실시간 push 전담. ChatService 는 무슨 일이 일어났는지만 정하고, 그걸 소켓으로
 * 흘려보내는 동작은 전부 여기서 함 — 트랜잭션 커밋 시점을 기다리는 것도 이 책임이라 같이 묶음.
 */
@Service
@RequiredArgsConstructor
public class ChatWebSocketService {

	// 목록 화면 전용 큐 — 방 단위 큐(queueOf)와 분리해서 목록이 열려 있을 때만 한 행을 갱신시킴
	private static final String LIST_QUEUE = "/queue/chat-rooms-list";

	private final SimpMessagingTemplate messagingTemplate;

	// 메시지를 한 유저 큐로 push 함 — 발신자 본인 큐로도 보내 다른 기기와 동기화함
	public void pushMessage(Long roomId, Long userId, ChatMessageResponse response) {
		afterCommit(() -> messagingTemplate.convertAndSendToUser(String.valueOf(userId), queueOf(roomId), response));
	}

	// 지정한 유저 한 명에게만 보냄 — pushMessage 처럼 양쪽에 보낼지는 호출부(ChatService)가 정함
	public void pushReadEvent(Long roomId, Long userId, ChatReadEventResponse event) {
		afterCommit(() -> messagingTemplate.convertAndSendToUser(String.valueOf(userId), queueOf(roomId) + "/read", event));
	}

	// 상대가 방을 보고 있지 않을 때만 호출됨(ChatService 판단) — 목록의 그 행 하나만 갱신시킴
	public void pushRoomListUpdate(Long userId, ChatRoomListUpdateResponse response) {
		afterCommit(() -> messagingTemplate.convertAndSendToUser(String.valueOf(userId), LIST_QUEUE, response));
	}

	// 트랜잭션이 끝까지 커밋된 뒤에만 보냄 — 커밋 전에 push 하면 받은 쪽이 아직 DB 에 없는
	// 메시지를 다시 조회로 확인하는 레이스가 생김. 트랜잭션 없이 불리면(단위 테스트 등) 동기화가
	// 활성화돼 있지 않으니 그냥 바로 실행함
	private void afterCommit(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					action.run();
				}
			});
		} else {
			action.run();
		}
	}

	// 방마다 큐를 나눠서 같은 유저가 여러 방에 있어도 메시지가 섞이지 않음 — 클라이언트 구독 경로와 정확히 일치해야 함
	private String queueOf(Long roomId) {
		return "/queue/chat-rooms/" + roomId;
	}
}
