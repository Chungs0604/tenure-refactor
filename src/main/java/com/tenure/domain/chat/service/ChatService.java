package com.tenure.domain.chat.service;

import com.tenure.domain.block.repository.UserBlockRepository;
import com.tenure.domain.chat.dto.request.ChatMessageSendRequest;
import com.tenure.domain.chat.dto.request.ChatReadRequest;
import com.tenure.domain.chat.dto.response.ChatCtaResponse;
import com.tenure.domain.chat.dto.response.ChatMessageResponse;
import com.tenure.domain.chat.dto.response.ChatReadEventResponse;
import com.tenure.domain.chat.dto.response.ChatRoomHeaderResponse;
import com.tenure.domain.chat.dto.response.ChatRoomOpenResponse;
import com.tenure.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.tenure.domain.chat.entity.ChatMessage;
import com.tenure.domain.chat.entity.ChatMessageImage;
import com.tenure.domain.chat.entity.ChatRoom;
import com.tenure.domain.chat.entity.ChatRoomMember;
import com.tenure.domain.chat.enums.ChatCtaAction;
import com.tenure.domain.chat.enums.ChatMessageType;
import com.tenure.domain.chat.enums.ChatRoomTab;
import com.tenure.domain.chat.exception.ChatErrorCode;
import com.tenure.domain.chat.mapper.ChatMapper;
import com.tenure.domain.chat.repository.ChatMessageImageRepository;
import com.tenure.domain.chat.repository.ChatMessageRepository;
import com.tenure.domain.chat.repository.ChatRoomFeedRow;
import com.tenure.domain.chat.repository.ChatRoomMemberRepository;
import com.tenure.domain.chat.repository.ChatRoomRepository;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.item.repository.ItemWearRecordRepository;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.domain.notification.enums.NotificationSettingType;
import com.tenure.domain.notification.enums.NotificationTargetType;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.offer.enums.OfferStatus;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.offer.repository.PurchaseOfferRepository;
import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.order.repository.TradeOrderRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CommonErrorCode;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.storage.FileStorage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 채팅방·메시지. 메시지 전송은 WebSocket 진입점({@code ChatWebSocketController})에서도 이 Service를
 * 그대로 호출한다 — 전송 경로가 둘이어도 검증·저장·알림·브로드캐스트는 한 곳에서 끝낸다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

	private final ChatRoomRepository chatRoomRepository;
	private final ChatRoomMemberRepository chatRoomMemberRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final ChatMessageImageRepository chatMessageImageRepository;
	private final ItemRepository itemRepository;
	private final ItemListingRepository itemListingRepository;
	private final ItemOwnershipRepository itemOwnershipRepository;
	private final ItemWearRecordRepository itemWearRecordRepository;
	private final PurchaseOfferRepository purchaseOfferRepository;
	private final TradeOrderRepository tradeOrderRepository;
	private final UserBlockRepository userBlockRepository;
	private final UserRepository userRepository;
	private final NotificationService notificationService;
	private final ChatMapper chatMapper;
	private final FileStorage fileStorage;
	private final SimpMessagingTemplate messagingTemplate;

	/** 채팅방 목록(API-180) */
	@Transactional(readOnly = true)
	@Loggable("채팅방 목록 조회")
	public CursorSlice<ChatRoomSummaryResponse> list(Long viewerId, ChatRoomTab tab, String keyword, Cursor cursor, int size) {
		List<ChatRoomFeedRow> rows = chatRoomRepository.findAllForFeed(viewerId, tab, keyword, cursor, size);
		CursorSlice<ChatRoomFeedRow> slice = CursorSlice.of(
				rows, size, row -> Cursor.of(row.chatRoom().getLastMessageAt(), row.chatRoom().getId()));

		List<Long> itemIds = slice.getContent().stream().map(row -> row.chatRoom().getItem().getId()).toList();
		Map<Long, LocalDate> wornDates = lastWornDatesOf(itemIds);

		List<Long> roomIds = slice.getContent().stream().map(row -> row.chatRoom().getId()).toList();
		Map<Long, ChatMessage> latestMessages = roomIds.isEmpty() ? Map.of() : chatMessageRepository
				.findLatestByChatRoomIdIn(roomIds).stream()
				.collect(Collectors.toMap(m -> m.getChatRoom().getId(), m -> m));

		return slice.map(row -> toSummaryRow(row, viewerId, wornDates, latestMessages));
	}

	/** 채팅방 개설(API-181). 이미 있으면 기존 방을 돌려준다(멱등) */
	@Transactional
	@Loggable("채팅방 개설")
	public ChatRoomOpenResponse open(Long userId, Long itemId) {
		Item item = findActiveItem(itemId);
		User requester = findUser(userId);
		LocalDateTime now = LocalDateTime.now();

		ChatRoom room = item.getSaleStatus() == SaleStatus.ON_SALE
				? openForListing(item, requester, now)
				: openForUnsold(item, requester, now);

		return ChatRoomOpenResponse.builder().roomId(room.getId()).build();
	}

	/** 채팅방 헤더(API-182) — 아이템·가격·거래 단계 CTA */
	@Transactional(readOnly = true)
	@Loggable("채팅방 헤더 조회")
	public ChatRoomHeaderResponse header(Long userId, Long roomId) {
		ChatRoom room = requireRoomDetail(roomId);
		requireActiveMember(roomId, userId);

		Item item = room.getItem();
		boolean isBuyer = room.getBuyer().getId().equals(userId);
		User counterpart = counterpartOf(room, userId);

		PurchaseOffer offer = resolveRelevantOffer(room);
		TradeOrder order = offer != null && offer.getStatus() == OfferStatus.ACCEPTED
				? tradeOrderRepository.findByOfferId(offer.getId()).orElse(null)
				: null;

		String price = resolvePrice(room, offer);
		List<ChatCtaResponse> ctas = List.of(buildLeftCta(item, isBuyer), buildRightCta(item, isBuyer, offer, order));
		LocalDate recentWornDate = lastWornDatesOf(List.of(item.getId())).get(item.getId());

		return chatMapper.toHeaderResponse(
				room.getId(),
				chatMapper.toHeaderCounterpartResponse(counterpart),
				chatMapper.toItemSummaryResponse(item, fileStorage.toUrl(item.getCoverImageKey())),
				price,
				item.getSaleStatus(),
				recentWornDate,
				ctas,
				order != null ? order.getId() : null);
	}

	/** 메시지 목록(API-183). 최신부터 거슬러 올라간다 */
	@Transactional(readOnly = true)
	@Loggable("채팅 메시지 목록 조회")
	public CursorSlice<ChatMessageResponse> messages(Long userId, Long roomId, Cursor cursor, int size) {
		requireRoom(roomId);
		requireActiveMember(roomId, userId);

		Long cursorId = cursor != null ? cursor.getId() : null;
		List<ChatMessage> rows = chatMessageRepository.findAllByChatRoomId(roomId, cursorId, PageRequest.of(0, size + 1));
		CursorSlice<ChatMessage> slice = CursorSlice.of(rows, size, m -> Cursor.of(m.getId()));

		Map<Long, List<String>> imagesByMessage = imagesOf(slice.getContent());

		return slice.map(m -> toMessageResponse(m, imagesByMessage));
	}

	/** 메시지 전송. WebSocket 진입점이 이 메서드를 직접 부른다(API-184 는 WS 프로토콜로 대체) */
	@Transactional
	@Loggable("채팅 메시지 전송")
	public ChatMessageResponse sendMessage(Long senderId, Long roomId, ChatMessageSendRequest request) {
		ChatRoom room = requireRoom(roomId);
		requireActiveMember(roomId, senderId);

		User sender = senderId.equals(room.getBuyer().getId()) ? room.getBuyer() : room.getSeller();
		User counterpart = counterpartOf(room, senderId);
		requireNotBlocked(sender, counterpart);

		if (request.getMessageType() == null) {
			throw new CustomException(ChatErrorCode.CHAT_400_INVALID_MESSAGE);
		}

		ChatMessage message;
		List<String> imageUrls;
		if (request.getMessageType() == ChatMessageType.TEXT) {
			if (!StringUtils.hasText(request.getContent())) {
				throw new CustomException(ChatErrorCode.CHAT_400_INVALID_MESSAGE);
			}
			message = chatMessageRepository.save(ChatMessage.ofText(room, sender, request.getContent()));
			imageUrls = null;
		} else {
			if (request.getImageKeys() == null || request.getImageKeys().isEmpty()) {
				throw new CustomException(ChatErrorCode.CHAT_400_INVALID_MESSAGE);
			}
			message = chatMessageRepository.save(ChatMessage.ofImage(room, sender));
			imageUrls = saveImages(message, request.getImageKeys());
		}

		// IDENTITY 전략이라 save 호출 시점에 바로 INSERT 됨 — createdAt(@CreatedDate)도 이때 채워짐
		room.updateLastMessageAt(message.getCreatedAt());

		notificationService.notify(
				counterpart, NotificationSettingType.CHAT, NotificationCategory.ACTION_REQUIRED,
				room.getItem().getName(), "새 메시지가 왔어요.",
				room.getItem().getCoverImageKey(), NotificationTargetType.CHAT_ROOM, room.getId());

		ChatMessageResponse response = chatMapper.toMessageResponse(
				message,
				request.getMessageType() == ChatMessageType.TEXT ? message.getContent() : null,
				imageUrls);

		messagingTemplate.convertAndSendToUser(String.valueOf(sender.getId()), queueOf(roomId), response);
		messagingTemplate.convertAndSendToUser(String.valueOf(counterpart.getId()), queueOf(roomId), response);

		return response;
	}

	/** 읽음 처리(API-185) + 상대방에게 실시간 push */
	@Transactional
	@Loggable("채팅 읽음 처리")
	public void read(Long userId, Long roomId, ChatReadRequest request) {
		ChatRoom room = requireRoom(roomId);
		ChatRoomMember member = requireActiveMember(roomId, userId);

		ChatMessage lastReadMessage = requireMessageInRoom(request.getLastReadMessageId(), roomId);
		member.markRead(lastReadMessage);

		User counterpart = counterpartOf(room, userId);
		messagingTemplate.convertAndSendToUser(
				String.valueOf(counterpart.getId()), queueOf(roomId) + "/read",
				ChatReadEventResponse.builder()
						.roomId(roomId)
						.lastReadMessageId(request.getLastReadMessageId())
						.build());
	}

	/** 채팅방 나가기(API-186). 방·메시지는 남고 이 참여자의 leftAt 만 찍힌다 */
	@Transactional
	@Loggable("채팅방 나가기")
	public void leave(Long userId, Long roomId) {
		requireRoom(roomId);
		ChatRoomMember member = requireActiveMember(roomId, userId);
		member.leave(LocalDateTime.now());
	}

	// ── 개설 보조 ───────────────────────────────────────────────

	/** 판매중 아이템. listing 이 가격 출처 — 구매 제안·거래 의사와 무관하게 독립적으로 연다 */
	private ChatRoom openForListing(Item item, User requester, LocalDateTime now) {
		ItemListing listing = itemListingRepository.findOpenByItemId(item.getId())
				.orElseThrow(() -> {
					log.info("판매중인데 열린 판매 등록이 없는 아이템임 itemId={}", item.getId());
					return new CustomException(ItemErrorCode.ITEM_404);
				});
		User seller = listing.getSeller();
		requireNotSelf(requester, seller);
		requireNotBlocked(requester, seller);

		ChatRoom room = chatRoomRepository.findByItemIdAndBuyerIdAndSellerId(item.getId(), requester.getId(), seller.getId())
				.orElseGet(() -> chatRoomRepository.save(ChatRoom.openForListing(item, listing, requester, seller, now)));
		ensureMembers(room, requester, seller);
		return room;
	}

	/** 미판매 아이템. offerAllowed 가 꺼져 있으면 애초에 열 수 없다 */
	private ChatRoom openForUnsold(Item item, User requester, LocalDateTime now) {
		User seller = currentOwner(item.getId());
		requireNotSelf(requester, seller);

		if (!Boolean.TRUE.equals(item.getOfferAllowed())) {
			log.info("구매 제안을 받지 않는 미판매 상품에 채팅을 시도함 itemId={}", item.getId());
			throw new CustomException(ChatErrorCode.CHAT_400_NOT_OPENABLE);
		}

		requireNotBlocked(requester, seller);

		ChatRoom room = chatRoomRepository.findByItemIdAndBuyerIdAndSellerId(item.getId(), requester.getId(), seller.getId())
				.orElseGet(() -> chatRoomRepository.save(ChatRoom.openForUnsold(item, requester, seller, now)));
		ensureMembers(room, requester, seller);
		return room;
	}

	private void ensureMembers(ChatRoom room, User buyer, User seller) {
		if (chatRoomMemberRepository.findByChatRoomIdAndUserId(room.getId(), buyer.getId()).isEmpty()) {
			chatRoomMemberRepository.save(ChatRoomMember.join(room, buyer));
		}
		if (chatRoomMemberRepository.findByChatRoomIdAndUserId(room.getId(), seller.getId()).isEmpty()) {
			chatRoomMemberRepository.save(ChatRoomMember.join(room, seller));
		}
	}

	// ── 헤더 CTA ───────────────────────────────────────────────

	/** 좌측 버튼 — 판매 상태 × 역할. 항상 활성 */
	private ChatCtaResponse buildLeftCta(Item item, boolean isBuyer) {
		boolean onSale = item.getSaleStatus() == SaleStatus.ON_SALE;
		String label = onSale
				? (isBuyer ? "상품 상세" : "상품 관리")
				: (isBuyer ? "아이템 상세" : "아이템 관리");
		ChatCtaAction action = isBuyer ? ChatCtaAction.ITEM_DETAIL : ChatCtaAction.ITEM_MANAGE;
		return ChatCtaResponse.builder().label(label).action(action).targetId(item.getId()).enabled(true).build();
	}

	/** 우측 버튼 — 관련 제안의 거래 단계. design-audit.md G-2 표 그대로 */
	private ChatCtaResponse buildRightCta(Item item, boolean isBuyer, PurchaseOffer offer, TradeOrder order) {
		if (offer == null) {
			return ChatCtaResponse.builder()
					.label("구매하기").action(ChatCtaAction.PURCHASE).targetId(item.getId()).enabled(true).build();
		}

		return switch (offer.getStatus()) {
			case PENDING -> {
				boolean isTradeIntent = offer.getOfferType() == OfferType.TRADE_INTENT;
				String label = isBuyer
						? (isTradeIntent ? "보낸 요청 보기" : "보낸 제안 보기")
						: (isTradeIntent ? "요청 확인" : "제안 확인");
				// 미판매+구매제안만 offerAllowed 전환에 영향을 받는다 — 거래 의사는 늘 활성
				boolean enabled = isTradeIntent || Boolean.TRUE.equals(item.getOfferAllowed());
				yield ChatCtaResponse.builder()
						.label(label).action(ChatCtaAction.OFFER_DETAIL).targetId(offer.getId()).enabled(enabled).build();
			}
			case ACCEPTED -> ChatCtaResponse.builder()
					.label("거래 상세").action(ChatCtaAction.ORDER_DETAIL)
					.targetId(order != null ? order.getId() : null).enabled(true).build();
			case REJECTED, CANCELED, EXPIRED -> ChatCtaResponse.builder()
					.label("제안 종료됨").action(ChatCtaAction.OFFER_DETAIL).targetId(offer.getId()).enabled(false).build();
		};
	}

	/** 이 방과 관련된 제안. 미판매 방은 chat_room.purchase_offer_id, 판매중 방은 최신 제안을 따로 찾는다 */
	private PurchaseOffer resolveRelevantOffer(ChatRoom room) {
		if (room.getPurchaseOffer() != null) {
			return room.getPurchaseOffer();
		}
		return purchaseOfferRepository
				.findLatestByItemIdAndBuyerId(room.getItem().getId(), room.getBuyer().getId())
				.orElse(null);
	}

	/** listing 이 있으면 그 가격, 없으면 관련 제안 금액, 둘 다 없으면(제안 전) null */
	private String resolvePrice(ChatRoom room, PurchaseOffer offer) {
		if (room.getListing() != null) {
			return String.valueOf(room.getListing().getPrice());
		}
		return offer != null ? String.valueOf(offer.getOfferPrice()) : null;
	}

	// ── 메시지 보조 ───────────────────────────────────────────────

	private List<String> saveImages(ChatMessage message, List<String> imageKeys) {
		List<ChatMessageImage> images = new ArrayList<>();
		for (int i = 0; i < imageKeys.size(); i++) {
			images.add(ChatMessageImage.of(message, imageKeys.get(i), i));
		}
		chatMessageImageRepository.saveAll(images);
		return images.stream().map(img -> fileStorage.toUrl(img.getImageKey())).toList();
	}

	private Map<Long, List<String>> imagesOf(Collection<ChatMessage> messages) {
		List<Long> imageMessageIds = messages.stream()
				.filter(m -> m.getMessageType() == ChatMessageType.IMAGE)
				.map(ChatMessage::getId)
				.toList();

		if (imageMessageIds.isEmpty()) {
			return Map.of();
		}

		return chatMessageImageRepository.findAllByChatMessageIdIn(imageMessageIds).stream()
				.collect(Collectors.groupingBy(
						img -> img.getChatMessage().getId(),
						Collectors.mapping(img -> fileStorage.toUrl(img.getImageKey()), Collectors.toList())));
	}

	private ChatMessageResponse toMessageResponse(ChatMessage message, Map<Long, List<String>> imagesByMessage) {
		String content = message.getMessageType() == ChatMessageType.TEXT ? message.getContent() : null;
		List<String> images = message.getMessageType() == ChatMessageType.IMAGE
				? imagesByMessage.getOrDefault(message.getId(), List.of())
				: null;
		return chatMapper.toMessageResponse(message, content, images);
	}

	private String queueOf(Long roomId) {
		return "/queue/chat-rooms/" + roomId;
	}

	// ── 목록 보조 ───────────────────────────────────────────────

	private ChatRoomSummaryResponse toSummaryRow(
			ChatRoomFeedRow row, Long viewerId, Map<Long, LocalDate> wornDates, Map<Long, ChatMessage> latestMessages
	) {
		ChatRoom room = row.chatRoom();
		Item item = room.getItem();
		User counterpart = counterpartOf(room, viewerId);
		ChatMessage latest = latestMessages.get(room.getId());

		return chatMapper.toRoomSummaryResponse(
				room.getId(),
				chatMapper.toCounterpartResponse(counterpart, fileStorage.toUrl(counterpart.getProfileImageKey())),
				previewOf(latest),
				room.getLastMessageAt(),
				(int) row.unreadCount(),
				chatMapper.toItemSummaryResponse(item, fileStorage.toUrl(item.getCoverImageKey())),
				wornDates.get(item.getId()));
	}

	/** 사진 메시지는 미리보기 문구로 대신한다 — 한 말풍선에 여러 장이라 URL 하나로 대표할 수 없음 */
	private String previewOf(ChatMessage latest) {
		if (latest == null) {
			return null;
		}
		return latest.getMessageType() == ChatMessageType.IMAGE ? "사진을 보냈습니다" : latest.getContent();
	}

	// ── 공통 보조 ───────────────────────────────────────────────

	private User counterpartOf(ChatRoom room, Long viewerId) {
		return room.getBuyer().getId().equals(viewerId) ? room.getSeller() : room.getBuyer();
	}

	private void requireNotSelf(User requester, User seller) {
		if (requester.getId().equals(seller.getId())) {
			log.info("본인 상품에 채팅을 시도함 userId={}", requester.getId());
			throw new CustomException(ChatErrorCode.CHAT_400_SELF);
		}
	}

	private void requireNotBlocked(User a, User b) {
		if (userBlockRepository.existsBlock(a.getId(), b.getId()) || userBlockRepository.existsBlock(b.getId(), a.getId())) {
			log.info("차단 관계인 상대와 채팅을 시도함 userId={} counterpartId={}", a.getId(), b.getId());
			throw new CustomException(ChatErrorCode.CHAT_403_BLOCKED);
		}
	}

	private ChatRoom requireRoom(Long roomId) {
		return chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new CustomException(ChatErrorCode.CHAT_404_ROOM));
	}

	private ChatRoom requireRoomDetail(Long roomId) {
		return chatRoomRepository.findDetailById(roomId)
				.orElseThrow(() -> new CustomException(ChatErrorCode.CHAT_404_ROOM));
	}

	private ChatRoomMember requireActiveMember(Long roomId, Long userId) {
		ChatRoomMember member = chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
				.orElseThrow(() -> new CustomException(ChatErrorCode.CHAT_403));
		if (member.getLeftAt() != null) {
			log.info("나간 채팅방에 접근을 시도함 roomId={} userId={}", roomId, userId);
			throw new CustomException(ChatErrorCode.CHAT_403);
		}
		return member;
	}

	/** 읽음 처리 대상 메시지. 다른 방의 메시지 id 를 보내는 입력 오류를 막는다 */
	private ChatMessage requireMessageInRoom(Long messageId, Long roomId) {
		ChatMessage message = chatMessageRepository.findById(messageId)
				.orElseThrow(() -> new CustomException(CommonErrorCode.BAD_REQUEST));
		if (!message.getChatRoom().getId().equals(roomId)) {
			log.info("다른 방의 메시지로 읽음 처리를 시도함 messageId={} roomId={}", messageId, roomId);
			throw new CustomException(CommonErrorCode.BAD_REQUEST);
		}
		return message;
	}

	private Item findActiveItem(Long itemId) {
		return itemRepository.findActive(itemId)
				.orElseThrow(() -> {
					log.info("없거나 삭제된 아이템임 itemId={}", itemId);
					return new CustomException(ItemErrorCode.ITEM_404);
				});
	}

	private User currentOwner(Long itemId) {
		return itemOwnershipRepository.findCurrent(itemId)
				.map(ItemOwnership::getOwner)
				.orElseThrow(() -> {
					log.info("소유 구간이 없는 아이템임 itemId={}", itemId);
					return new CustomException(ItemErrorCode.ITEM_404);
				});
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> {
					log.info("없는 사용자임 userId={}", userId);
					return new CustomException(UserErrorCode.USER_404);
				});
	}

	private Map<Long, LocalDate> lastWornDatesOf(Collection<Long> itemIds) {
		if (itemIds.isEmpty()) {
			return Map.of();
		}
		return itemWearRecordRepository.findLastWornDates(itemIds).stream()
				.collect(Collectors.toMap(row -> (Long) row[0], row -> (LocalDate) row[1]));
	}
}
