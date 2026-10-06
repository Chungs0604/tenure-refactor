package com.tenure.domain.chat.repository;

import com.tenure.domain.chat.entity.ChatRoom;

/** 채팅방 목록 한 행 + 이 조회자 기준 안읽음 수. Service 가 응답으로 바꾸기 전 중간 형태임 */
public record ChatRoomFeedRow(ChatRoom chatRoom, long unreadCount) {
}
