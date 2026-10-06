package com.tenure.domain.chat.controller;

import com.tenure.domain.chat.dto.request.ChatReadRequest;
import com.tenure.domain.chat.dto.response.ChatMessageResponse;
import com.tenure.domain.chat.dto.response.ChatRoomHeaderResponse;
import com.tenure.domain.chat.dto.response.ChatRoomOpenResponse;
import com.tenure.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.tenure.domain.chat.enums.ChatRoomTab;
import com.tenure.global.pagination.CursorResponse;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
@Tag(name = "채팅", description = "채팅방 목록·개설·헤더·메시지 API. 메시지 전송은 WebSocket(/app/chat-rooms/{roomId}/messages)")
public interface ChatApi {

	@Operation(summary = "채팅방 목록", description = "탭(전체/구매/판매/읽지않음) + 닉네임·아이템명 검색. 나간 방은 빠진다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공")
	})
	ResponseEntity<BaseResponse<CursorResponse<ChatRoomSummaryResponse>>> getRooms(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "탭") ChatRoomTab tab,
			@Parameter(description = "검색어") String keyword,
			@Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 비운다") String cursor,
			@Parameter(description = "페이지 크기") @Min(1) @Max(100) int size);

	@Operation(summary = "채팅방 개설",
			description = "판매중 아이템은 listing 기준, 미판매 아이템은 offerAllowed 가 꺼져 있으면 열 수 없음. "
					+ "이미 있는 방이면 그 방을 그대로 돌려준다(멱등)")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "개설 성공"),
			@ApiResponse(responseCode = "400", description = "CHAT_400_NOT_OPENABLE / CHAT_400_SELF"),
			@ApiResponse(responseCode = "403", description = "CHAT_403_BLOCKED"),
			@ApiResponse(responseCode = "404", description = "ITEM_404")
	})
	ResponseEntity<BaseResponse<ChatRoomOpenResponse>> openRoom(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "아이템 id") Long itemId);

	@Operation(summary = "채팅방 헤더", description = "아이템·가격·판매 상태와 거래 단계별 CTA 버튼 2개")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "403", description = "CHAT_403"),
			@ApiResponse(responseCode = "404", description = "CHAT_404_ROOM")
	})
	ResponseEntity<BaseResponse<ChatRoomHeaderResponse>> getRoomHeader(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "채팅방 id") Long roomId);

	@Operation(summary = "메시지 목록", description = "최신부터 커서로 거슬러 올라간다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "403", description = "CHAT_403"),
			@ApiResponse(responseCode = "404", description = "CHAT_404_ROOM")
	})
	ResponseEntity<BaseResponse<CursorResponse<ChatMessageResponse>>> getMessages(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "채팅방 id") Long roomId,
			@Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 비운다") String cursor,
			@Parameter(description = "페이지 크기") @Min(1) @Max(100) int size);

	@Operation(summary = "읽음 처리", description = "처리 즉시 상대방에게 WS 로 읽음 이벤트를 push 한다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "처리 성공"),
			@ApiResponse(responseCode = "400", description = "COMMON_400 — 이 방의 메시지가 아님"),
			@ApiResponse(responseCode = "403", description = "CHAT_403")
	})
	ResponseEntity<BaseResponse<Void>> read(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "채팅방 id") Long roomId,
			@Valid ChatReadRequest request);

	@Operation(summary = "채팅방 나가기", description = "방과 메시지는 남고 내 참여 상태만 종료된다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "나가기 성공"),
			@ApiResponse(responseCode = "403", description = "CHAT_403")
	})
	ResponseEntity<BaseResponse<Void>> leave(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "채팅방 id") Long roomId);
}
