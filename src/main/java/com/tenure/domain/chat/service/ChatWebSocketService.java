package com.tenure.domain.chat.service;

import com.tenure.domain.chat.dto.response.ChatMessageResponse;
import com.tenure.domain.chat.dto.response.ChatReadEventResponse;
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

	private final SimpMessagingTemplate messagingTemplate;

	// 메시지를 한 유저 큐로 push 함 — 발신자 본인 큐로도 보내 다른 기기와 동기화함
	public void pushMessage(Long roomId, Long userId, ChatMessageResponse response) {
		afterCommit(() -> messagingTemplate.convertAndSendToUser(String.valueOf(userId), queueOf(roomId), response));
	}

	// 읽음 이벤트를 상대방 큐로 push 함
	public void pushReadEvent(Long roomId, Long userId, ChatReadEventResponse event) {
		afterCommit(() -> messagingTemplate.convertAndSendToUser(String.valueOf(userId), queueOf(roomId) + "/read", event));
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

	private String queueOf(Long roomId) {
		return "/queue/chat-rooms/" + roomId;
	}
}
