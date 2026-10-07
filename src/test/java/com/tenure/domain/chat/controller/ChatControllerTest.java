package com.tenure.domain.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tenure.domain.chat.dto.request.ChatReadRequest;
import com.tenure.domain.chat.dto.response.ChatHeaderCounterpartResponse;
import com.tenure.domain.chat.dto.response.ChatRoomHeaderResponse;
import com.tenure.domain.chat.dto.response.ChatRoomOpenResponse;
import com.tenure.domain.chat.enums.ChatRoomTab;
import com.tenure.domain.chat.exception.ChatErrorCode;
import com.tenure.domain.chat.service.ChatService;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.global.exception.CommonErrorCode;
import com.tenure.global.exception.CustomException;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorCodec;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.security.AuthUser;
import com.tenure.support.ControllerSliceTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(ChatController.class)
@Import(CursorCodec.class)
@DisplayName("ChatController")
class ChatControllerTest extends ControllerSliceTestSupport {

	private static final String ROOMS_URL = "/api/v1/chat-rooms";
	private static final Long USER_ID = 1L;
	private static final Long ROOM_ID = 10L;
	private static final Long ITEM_ID = 20L;

	@MockitoBean private ChatService chatService;

	@Nested
	@DisplayName("GET /api/v1/chat-rooms")
	class GetRooms {

		@Test
		@DisplayName("기본값은 ALL 탭·size 20 이다")
		void getRooms_noParams_usesDefaults() throws Exception {
			given(chatService.list(USER_ID, ChatRoomTab.ALL, null, null, 20)).willReturn(CursorSlice.empty());

			mvc.perform(get(ROOMS_URL).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.hasNext").value(false));

			verify(chatService).list(USER_ID, ChatRoomTab.ALL, null, null, 20);
		}

		@Test
		@DisplayName("탭·검색어·커서를 그대로 서비스에 넘긴다")
		void getRooms_withParams_passesThroughToService() throws Exception {
			Cursor cursor = Cursor.of(10L);
			String encoded = new CursorCodec().encode(cursor);
			given(chatService.list(USER_ID, ChatRoomTab.UNREAD, "지건", cursor, 5)).willReturn(CursorSlice.empty());

			mvc.perform(get(ROOMS_URL)
							.param("tab", "UNREAD")
							.param("keyword", "지건")
							.param("cursor", encoded)
							.param("size", "5")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk());

			verify(chatService).list(USER_ID, ChatRoomTab.UNREAD, "지건", cursor, 5);
		}

		@Test
		@DisplayName("깨진 커서는 400 이다")
		void getRooms_malformedCursor_returnsBadRequest() throws Exception {
			mvc.perform(get(ROOMS_URL).param("cursor", "!!broken!!").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON_400"));

			verifyNoInteractions(chatService);
		}

		@Test
		@DisplayName("size 가 0 이면 400 이다")
		void getRooms_sizeZero_returnsBadRequest() throws Exception {
			mvc.perform(get(ROOMS_URL).param("size", "0").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isBadRequest());

			verifyNoInteractions(chatService);
		}

		@Test
		@DisplayName("size 가 100 을 넘으면 400 이다")
		void getRooms_sizeTooLarge_returnsBadRequest() throws Exception {
			mvc.perform(get(ROOMS_URL).param("size", "101").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isBadRequest());

			verifyNoInteractions(chatService);
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void getRooms_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(get(ROOMS_URL))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("COMMON_401"));

			verifyNoInteractions(chatService);
		}
	}

	@Nested
	@DisplayName("POST /api/v1/items/{itemId}/chat-rooms")
	class OpenRoom {

		@Test
		@DisplayName("개설에 성공하면 roomId 를 내려준다")
		void openRoom_valid_returnsRoomId() throws Exception {
			given(chatService.open(USER_ID, ITEM_ID))
					.willReturn(ChatRoomOpenResponse.builder().roomId(ROOM_ID).build());

			mvc.perform(post("/api/v1/items/" + ITEM_ID + "/chat-rooms").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.roomId").value(ROOM_ID));
		}

		@Test
		@DisplayName("본인 상품이면 400 이다")
		void openRoom_selfItem_returnsBadRequest() throws Exception {
			willThrow(new CustomException(ChatErrorCode.CHAT_400_SELF)).given(chatService).open(USER_ID, ITEM_ID);

			mvc.perform(post("/api/v1/items/" + ITEM_ID + "/chat-rooms").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("CHAT_400_SELF"));
		}

		@Test
		@DisplayName("차단 관계면 403 이다")
		void openRoom_blocked_returnsForbidden() throws Exception {
			willThrow(new CustomException(ChatErrorCode.CHAT_403_BLOCKED)).given(chatService).open(USER_ID, ITEM_ID);

			mvc.perform(post("/api/v1/items/" + ITEM_ID + "/chat-rooms").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("CHAT_403_BLOCKED"));
		}

		@Test
		@DisplayName("없는 아이템이면 404 다")
		void openRoom_unknownItem_returnsNotFound() throws Exception {
			willThrow(new CustomException(ItemErrorCode.ITEM_404)).given(chatService).open(USER_ID, ITEM_ID);

			mvc.perform(post("/api/v1/items/" + ITEM_ID + "/chat-rooms").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("ITEM_404"));
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void openRoom_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(post("/api/v1/items/" + ITEM_ID + "/chat-rooms"))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(chatService);
		}
	}

	@Nested
	@DisplayName("GET /api/v1/chat-rooms/{roomId}")
	class GetRoomHeader {

		@Test
		@DisplayName("조회에 성공하면 헤더를 내려준다")
		void getRoomHeader_valid_returnsHeader() throws Exception {
			given(chatService.header(USER_ID, ROOM_ID)).willReturn(headerResponse());

			mvc.perform(get(ROOMS_URL + "/" + ROOM_ID).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.roomId").value(ROOM_ID))
					.andExpect(jsonPath("$.data.blocked").value(false));
		}

		@Test
		@DisplayName("참여 중인 방이 아니면 403 이다")
		void getRoomHeader_notMember_returnsForbidden() throws Exception {
			willThrow(new CustomException(ChatErrorCode.CHAT_403)).given(chatService).header(USER_ID, ROOM_ID);

			mvc.perform(get(ROOMS_URL + "/" + ROOM_ID).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("CHAT_403"));
		}

		@Test
		@DisplayName("없는 방이면 404 다")
		void getRoomHeader_unknownRoom_returnsNotFound() throws Exception {
			willThrow(new CustomException(ChatErrorCode.CHAT_404_ROOM)).given(chatService).header(USER_ID, ROOM_ID);

			mvc.perform(get(ROOMS_URL + "/" + ROOM_ID).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("CHAT_404_ROOM"));
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void getRoomHeader_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(get(ROOMS_URL + "/" + ROOM_ID))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(chatService);
		}
	}

	@Nested
	@DisplayName("GET /api/v1/chat-rooms/{roomId}/messages")
	class GetMessages {

		@Test
		@DisplayName("기본값은 size 30 이다")
		void getMessages_noParams_usesDefaultSize() throws Exception {
			given(chatService.messages(USER_ID, ROOM_ID, null, 30)).willReturn(CursorSlice.empty());

			mvc.perform(get(ROOMS_URL + "/" + ROOM_ID + "/messages").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk());

			verify(chatService).messages(USER_ID, ROOM_ID, null, 30);
		}

		@Test
		@DisplayName("size 가 100 을 넘으면 400 이다")
		void getMessages_sizeTooLarge_returnsBadRequest() throws Exception {
			mvc.perform(get(ROOMS_URL + "/" + ROOM_ID + "/messages").param("size", "101")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isBadRequest());

			verifyNoInteractions(chatService);
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void getMessages_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(get(ROOMS_URL + "/" + ROOM_ID + "/messages"))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(chatService);
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/chat-rooms/{roomId}/read")
	class Read {

		@Test
		@DisplayName("읽음 처리에 성공하면 200 이다")
		void read_valid_returnsOk() throws Exception {
			mvc.perform(patch(ROOMS_URL + "/" + ROOM_ID + "/read")
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(ChatReadRequest.builder().lastReadMessageId(5L).build())))
					.andExpect(status().isOk());

			verify(chatService).read(eq(USER_ID), eq(ROOM_ID), any(ChatReadRequest.class));
		}

		@Test
		@DisplayName("lastReadMessageId 가 없으면 400 이다")
		void read_missingLastReadMessageId_returnsBadRequest() throws Exception {
			mvc.perform(patch(ROOMS_URL + "/" + ROOM_ID + "/read")
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(ChatReadRequest.builder().build())))
					.andExpect(status().isBadRequest());

			verifyNoInteractions(chatService);
		}

		@Test
		@DisplayName("다른 방의 메시지 id 면 400 이다")
		void read_messageFromOtherRoom_returnsBadRequest() throws Exception {
			willThrow(new CustomException(CommonErrorCode.BAD_REQUEST))
					.given(chatService).read(eq(USER_ID), eq(ROOM_ID), any(ChatReadRequest.class));

			mvc.perform(patch(ROOMS_URL + "/" + ROOM_ID + "/read")
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(ChatReadRequest.builder().lastReadMessageId(5L).build())))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON_400"));
		}

		@Test
		@DisplayName("참여 중인 방이 아니면 403 이다")
		void read_notMember_returnsForbidden() throws Exception {
			willThrow(new CustomException(ChatErrorCode.CHAT_403))
					.given(chatService).read(eq(USER_ID), eq(ROOM_ID), any(ChatReadRequest.class));

			mvc.perform(patch(ROOMS_URL + "/" + ROOM_ID + "/read")
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(ChatReadRequest.builder().lastReadMessageId(5L).build())))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("CHAT_403"));
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void read_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(patch(ROOMS_URL + "/" + ROOM_ID + "/read")
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(ChatReadRequest.builder().lastReadMessageId(5L).build())))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(chatService);
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/chat-rooms/{roomId}/me")
	class Leave {

		@Test
		@DisplayName("나가기에 성공하면 200 이다")
		void leave_valid_returnsOk() throws Exception {
			mvc.perform(delete(ROOMS_URL + "/" + ROOM_ID + "/me").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk());

			verify(chatService).leave(USER_ID, ROOM_ID);
		}

		@Test
		@DisplayName("참여 중인 방이 아니면 403 이다")
		void leave_notMember_returnsForbidden() throws Exception {
			willThrow(new CustomException(ChatErrorCode.CHAT_403)).given(chatService).leave(USER_ID, ROOM_ID);

			mvc.perform(delete(ROOMS_URL + "/" + ROOM_ID + "/me").with(authentication(principalOf(USER_ID))))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("CHAT_403"));
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void leave_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(delete(ROOMS_URL + "/" + ROOM_ID + "/me"))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(chatService);
		}
	}

	private static UsernamePasswordAuthenticationToken principalOf(Long userId) {
		return new UsernamePasswordAuthenticationToken(new AuthUser(userId), null, List.of());
	}

	private static ChatRoomHeaderResponse headerResponse() {
		return ChatRoomHeaderResponse.builder()
				.roomId(ROOM_ID)
				.counterpart(ChatHeaderCounterpartResponse.builder().nickname("yujin_closet").build())
				.ctas(List.of())
				.blocked(false)
				.build();
	}
}
