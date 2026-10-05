package com.tenure.domain.chat.controller;

import com.tenure.domain.chat.dto.request.ChatReadRequest;
import com.tenure.domain.chat.dto.response.ChatMessageResponse;
import com.tenure.domain.chat.dto.response.ChatRoomHeaderResponse;
import com.tenure.domain.chat.dto.response.ChatRoomOpenResponse;
import com.tenure.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.tenure.domain.chat.enums.ChatRoomTab;
import com.tenure.domain.chat.service.ChatService;
import com.tenure.global.pagination.CursorCodec;
import com.tenure.global.pagination.CursorResponse;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatController implements ChatApi {

	private final ChatService chatService;
	private final CursorCodec cursorCodec;

	// 채팅방 목록
	@Override
	@GetMapping("/api/v1/chat-rooms")
	public ResponseEntity<BaseResponse<CursorResponse<ChatRoomSummaryResponse>>> getRooms(
			@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(defaultValue = "ALL") ChatRoomTab tab,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		CursorSlice<ChatRoomSummaryResponse> slice =
				chatService.list(authUser.id(), tab, keyword, cursorCodec.decode(cursor), size);
		return ResponseEntity.ok(BaseResponse.success(cursorCodec.toResponse(slice)));
	}

	// 채팅방 개설
	@Override
	@PostMapping("/api/v1/items/{itemId}/chat-rooms")
	public ResponseEntity<BaseResponse<ChatRoomOpenResponse>> openRoom(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long itemId
	) {
		ChatRoomOpenResponse response = chatService.open(authUser.id(), itemId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 채팅방 헤더
	@Override
	@GetMapping("/api/v1/chat-rooms/{roomId}")
	public ResponseEntity<BaseResponse<ChatRoomHeaderResponse>> getRoomHeader(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long roomId
	) {
		ChatRoomHeaderResponse response = chatService.header(authUser.id(), roomId);
		return ResponseEntity.ok(BaseResponse.success(response));
	}

	// 메시지 목록
	@Override
	@GetMapping("/api/v1/chat-rooms/{roomId}/messages")
	public ResponseEntity<BaseResponse<CursorResponse<ChatMessageResponse>>> getMessages(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long roomId,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "30") int size
	) {
		CursorSlice<ChatMessageResponse> slice =
				chatService.messages(authUser.id(), roomId, cursorCodec.decode(cursor), size);
		return ResponseEntity.ok(BaseResponse.success(cursorCodec.toResponse(slice)));
	}

	// 읽음 처리
	@Override
	@PatchMapping("/api/v1/chat-rooms/{roomId}/read")
	public ResponseEntity<BaseResponse<Void>> read(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long roomId,
			@RequestBody ChatReadRequest request
	) {
		chatService.read(authUser.id(), roomId, request);
		return ResponseEntity.ok(BaseResponse.success());
	}

	// 채팅방 나가기
	@Override
	@DeleteMapping("/api/v1/chat-rooms/{roomId}/me")
	public ResponseEntity<BaseResponse<Void>> leave(
			@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long roomId
	) {
		chatService.leave(authUser.id(), roomId);
		return ResponseEntity.ok(BaseResponse.success());
	}
}
