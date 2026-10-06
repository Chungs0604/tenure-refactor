package com.tenure.domain.chat.mapper;

import com.tenure.domain.chat.dto.response.ChatCounterpartResponse;
import com.tenure.domain.chat.dto.response.ChatCtaResponse;
import com.tenure.domain.chat.dto.response.ChatHeaderCounterpartResponse;
import com.tenure.domain.chat.dto.response.ChatItemSummaryResponse;
import com.tenure.domain.chat.dto.response.ChatMessageResponse;
import com.tenure.domain.chat.dto.response.ChatRoomHeaderResponse;
import com.tenure.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.tenure.domain.chat.entity.ChatMessage;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.mapper.UserMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 엔티티 → 응답 단방향 변환.
 *
 * 저장소 키를 URL 로 바꾸는 일과 가격·안읽음수를 계산하는 일은 Service 가 끝내고 넘김 — 여기는
 * 이름이 맞는 값을 그대로 옮기기만 함
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ChatMapper {

	@Mapping(target = "nickname", expression = "java(displayNickname(user))")
	@Mapping(target = "profileImageUrl", source = "profileImageUrl")
	ChatCounterpartResponse toCounterpartResponse(User user, String profileImageUrl);

	@Mapping(target = "nickname", expression = "java(displayNickname(user))")
	ChatHeaderCounterpartResponse toHeaderCounterpartResponse(User user);

	@Mapping(target = "brandName", source = "item.brandName")
	@Mapping(target = "name", source = "item.name")
	@Mapping(target = "coverImageUrl", source = "coverImageUrl")
	ChatItemSummaryResponse toItemSummaryResponse(Item item, String coverImageUrl);

	@Mapping(target = "roomId", source = "roomId")
	@Mapping(target = "counterpart", source = "counterpart")
	@Mapping(target = "lastMessage", source = "lastMessage")
	@Mapping(target = "lastMessageAt", source = "lastMessageAt")
	@Mapping(target = "unreadCount", source = "unreadCount")
	@Mapping(target = "item", source = "item")
	@Mapping(target = "recentWornDate", source = "recentWornDate")
	ChatRoomSummaryResponse toRoomSummaryResponse(
			Long roomId, ChatCounterpartResponse counterpart, String lastMessage, LocalDateTime lastMessageAt,
			Integer unreadCount, ChatItemSummaryResponse item, LocalDate recentWornDate);

	@Mapping(target = "roomId", source = "roomId")
	@Mapping(target = "counterpart", source = "counterpart")
	@Mapping(target = "item", source = "item")
	@Mapping(target = "price", source = "price")
	@Mapping(target = "saleStatus", source = "saleStatus")
	@Mapping(target = "recentWornDate", source = "recentWornDate")
	@Mapping(target = "ctas", source = "ctas")
	@Mapping(target = "orderId", source = "orderId")
	@Mapping(target = "counterpartLastReadMessageId", source = "counterpartLastReadMessageId")
	ChatRoomHeaderResponse toHeaderResponse(
			Long roomId, ChatHeaderCounterpartResponse counterpart, ChatItemSummaryResponse item, String price,
			SaleStatus saleStatus, LocalDate recentWornDate, List<ChatCtaResponse> ctas, Long orderId,
			Long counterpartLastReadMessageId);

	@Mapping(target = "id", source = "message.id")
	@Mapping(target = "roomId", source = "message.chatRoom.id")
	@Mapping(target = "senderId", source = "message.sender.id")
	@Mapping(target = "messageType", source = "message.messageType")
	@Mapping(target = "content", source = "content")
	@Mapping(target = "images", source = "images")
	@Mapping(target = "createdAt", source = "message.createdAt")
	ChatMessageResponse toMessageResponse(ChatMessage message, String content, List<String> images);

	/** 탈퇴 계정은 저장된 닉네임 대신 고정 문구를 보여줌 — OrderMapper 와 같은 기준 */
	default String displayNickname(User user) {
		return user.isWithdrawn() ? UserMapper.WITHDRAWN_NICKNAME : user.getNickname();
	}
}
