package com.tenure.domain.chat.service;

import com.tenure.domain.block.repository.UserBlockRepository;
import com.tenure.domain.chat.dto.request.ChatMessageSendRequest;
import com.tenure.domain.chat.dto.request.ChatReadRequest;
import com.tenure.domain.chat.dto.response.ChatCtaResponse;
import com.tenure.domain.chat.dto.response.ChatMessageResponse;
import com.tenure.domain.chat.dto.response.ChatReadEventResponse;
import com.tenure.domain.chat.dto.response.ChatRoomHeaderResponse;
import com.tenure.domain.chat.dto.response.ChatRoomListUpdateResponse;
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
import com.tenure.domain.item.repository.ItemLastWornDateRow;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/*
 * 채팅방·메시지 비즈니스 로직. 실시간 push 는 직접 하지 않고 ChatWebSocketService 에 맡김 —
 * 여기는 "무슨 일이 일어났는지"만 정하고, "그걸 어떻게 실시간으로 알리는지"는 그쪽 책임임.
 * 메시지 전송은 WebSocket 진입점(ChatWebSocketController)이 sendMessage 를 그대로 호출함.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
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
	private final ChatWebSocketService chatWebSocketService;
	private final ChatRoomPresenceRegistry presenceRegistry;

	/*
	 * this.attemptOpen(...) 으로 직접 부르면 AOP 프록시를 안 거쳐서 @Transactional(REQUIRES_NEW) 가
	 * 전혀 안 걸림(self-invocation 이 프록시를 건너뜀) — 그래서 자기 자신을 프록시로 다시 주입받아
	 * 그걸 거쳐 호출함. @Lazy 는 생성 시점의 순환 참조를 피하기 위함
	 */
	@Autowired
	@Lazy
	private ChatService self;

	// 채팅방 목록 조회
	@Loggable("채팅방 목록 조회")
	public CursorSlice<ChatRoomSummaryResponse> list(Long viewerId, ChatRoomTab tab, String keyword, Cursor cursor, int size) {
		// tab·keyword·커서로 걸러진 방 목록을 조회함(안읽음 수는 리포지토리가 같이 계산해 옴)
		List<ChatRoomFeedRow> rows = chatRoomRepository.findAllForFeed(viewerId, tab, keyword, cursor, size);
		CursorSlice<ChatRoomFeedRow> slice = CursorSlice.of(
				rows, size, row -> Cursor.of(row.chatRoom().getLastMessageAt(), row.chatRoom().getId()));

		// 화면에 나가는 아이템들의 최근 착용일을 한 번에 배치 조회함 — 행마다 쿼리하면 N+1 임
		List<Long> itemIds = slice.getContent().stream().map(row -> row.chatRoom().getItem().getId()).toList();
		Map<Long, LocalDate> wornDates = lastWornDatesOf(itemIds);

		// 방마다 미리보기로 쓸 최신 메시지도 같이 배치 조회함
		List<Long> roomIds = slice.getContent().stream().map(row -> row.chatRoom().getId()).toList();
		Map<Long, ChatMessage> latestMessages = roomIds.isEmpty() ? Map.of() : chatMessageRepository
				.findLatestByChatRoomIdIn(roomIds).stream()
				.collect(Collectors.toMap(m -> m.getChatRoom().getId(), m -> m));

		return slice.map(row -> toSummaryRow(row, viewerId, wornDates, latestMessages));
	}

	// 채팅방 개설. 이미 있으면 기존 방을 그대로 돌려줌(멱등)
	@Loggable("채팅방 개설")
	public ChatRoomOpenResponse open(Long userId, Long itemId) {
		try {
			return self.attemptOpen(userId, itemId);
		} catch (DataIntegrityViolationException e) {
			/*
			 * 유니크 제약을 한 번 위반하면 그 트랜잭션은 Hibernate 가 rollback-only 로 못 박아서,
			 * 안에서 잡고 이어서 써도(재조회 후 멤버 보장 등) 커밋 시점에 TransactionSystemException
			 * 이 남 — 그래서 실패한 시도는 그대로 롤백시키고, 완전히 새 트랜잭션으로 한 번 더
			 * 시도함(그때는 경쟁자가 이미 커밋해놔서 조회로 찾아짐)
			 */
			log.info("동시 요청으로 같은 채팅방·멤버가 먼저 생성됨 itemId={} userId={}", itemId, userId);
			return self.attemptOpen(userId, itemId);
		}
	}

	// open() 의 실제 시도. REQUIRES_NEW 라 open() 에 걸린 트랜잭션(없음)과 무관하게 매 호출이 독립 트랜잭션임
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ChatRoomOpenResponse attemptOpen(Long userId, Long itemId) {

		// 대상 아이템·요청자를 조회하고 개설 시각을 한 번만 고정해 하위 호출에 그대로 넘김
		Item item = findActiveItem(itemId);
		User requester = findUser(userId);
		LocalDateTime now = LocalDateTime.now();

		// 판매중이면 listing 기준으로, 미판매면 현재 소유자 기준으로 연다 — 둘은 서로 다른 흐름임
		ChatRoom room = item.getSaleStatus() == SaleStatus.ON_SALE
				? openForListing(item, requester, now)
				: openForUnsold(item, requester, now);

		return ChatRoomOpenResponse.builder()
				.roomId(room.getId())
				.build();
	}

	// 채팅방 헤더 — 아이템·가격·거래 단계 CTA
	@Loggable("채팅방 헤더 조회")
	public ChatRoomHeaderResponse header(Long userId, Long roomId) {
		ChatRoom room = requireRoomDetail(roomId);
		requireActiveMember(roomId, userId);

		// 보는 사람이 구매자인지 가르고 상대방(반대 역할)을 정함
		Item item = room.getItem();
		boolean isBuyer = room.getBuyer().getId().equals(userId);
		User counterpart = counterpartOf(room, userId);

		// 관련 제안으로 거래 단계를 가리고, 수락된 제안이면 거래까지 찾아 orderId 를 채움
		PurchaseOffer offer = resolveRelevantOffer(room);
		TradeOrder order = offer != null && offer.getStatus() == OfferStatus.ACCEPTED
				? tradeOrderRepository.findByOfferId(offer.getId()).orElse(null)
				: null;

		// 가격·좌우 CTA·최근 착용일을 각각 계산해 응답에 조립할 값을 준비함
		String price = resolvePrice(room, offer);
		List<ChatCtaResponse> ctas = List.of(buildLeftCta(item, isBuyer), buildRightCta(item, isBuyer, offer, order));
		LocalDate recentWornDate = lastWornDatesOf(List.of(item.getId())).get(item.getId());

		// 상대가 지금까지 읽은 위치 — 들어오는 시점(재조회) 기준값이고, 그 뒤로는 WS 읽음 이벤트로 갱신됨
		Long counterpartLastReadMessageId = chatRoomMemberRepository
				.findByChatRoomIdAndUserId(roomId, counterpart.getId())
				.map(ChatRoomMember::getLastReadMessage)
				.map(ChatMessage::getId)
				.orElse(null);

		User viewer = isBuyer ? room.getBuyer() : room.getSeller();

		return chatMapper.toHeaderResponse(
				room.getId(),
				chatMapper.toHeaderCounterpartResponse(counterpart),
				chatMapper.toItemSummaryResponse(item, fileStorage.toUrl(item.getCoverImageKey())),
				price,
				item.getSaleStatus(),
				recentWornDate,
				ctas,
				order != null ? order.getId() : null,
				counterpartLastReadMessageId,
				isBlocked(viewer, counterpart));
	}

	// 메시지 목록. 최신부터 거슬러 올라감
	@Loggable("채팅 메시지 목록 조회")
	public CursorSlice<ChatMessageResponse> messages(Long userId, Long roomId, Cursor cursor, int size) {
		requireRoom(roomId);
		requireActiveMember(roomId, userId);

		// 커서 다음부터 size+1 개를 읽어 다음 페이지 유무를 판정함
		Long cursorId = cursor != null ? cursor.getId() : null;

		PageRequest request = PageRequest.of(0, size + 1);
		List<ChatMessage> rows = chatMessageRepository.findAllByChatRoomId(roomId, cursorId, request);
		CursorSlice<ChatMessage> slice = CursorSlice.of(rows, size, m -> Cursor.of(m.getId()));

		// IMAGE 메시지들의 사진을 한 번에 배치 조회함 — 메시지마다 쿼리하면 N+1 임
		Map<Long, List<String>> imagesByMessage = imagesOf(slice.getContent());

		return slice.map(m -> toMessageResponse(m, imagesByMessage));
	}

	// 메시지 전송. WebSocket 진입점이 이 메서드를 직접 호출함
	@Transactional
	@Loggable("채팅 메시지 전송")
	public ChatMessageResponse sendMessage(Long senderId, Long roomId, ChatMessageSendRequest request) {
		// 알림 발송 경로에서 room.getItem() 을 쓰므로 fetch join 으로 한 번에 가져옴 — N+1 방지
		ChatRoom room = requireRoomDetail(roomId);
		requireActiveMember(roomId, senderId);

		User sender = senderId.equals(room.getBuyer().getId()) ? room.getBuyer() : room.getSeller();
		User counterpart = counterpartOf(room, senderId);
		requireNotBlocked(sender, counterpart);
		requireValidMessage(request, roomId, senderId);

		// 타입별로 저장함 — TEXT 는 content, IMAGE 는 imageKeys 를 씀(필수 검증은 위에서 끝남)
		ChatMessage message;
		List<String> imageUrls;
		if (request.getMessageType() == ChatMessageType.TEXT) {
			message = chatMessageRepository.save(ChatMessage.ofText(room, sender, request.getContent()));
			imageUrls = null;
		} else {
			message = chatMessageRepository.save(ChatMessage.ofImage(room, sender));
			imageUrls = saveImages(message, request.getImageKeys());
		}

		// IDENTITY 전략이라 save 호출 시점에 바로 INSERT 됨 — createdAt(@CreatedDate)도 이때 채워짐
		room.updateLastMessageAt(message.getCreatedAt());

		ChatMessageResponse response = chatMapper.toMessageResponse(
				message,
				request.getMessageType() == ChatMessageType.TEXT ? message.getContent() : null,
				imageUrls);

		routeDelivery(room, roomId, sender, counterpart, message, response);

		return response;
	}

	// sendMessage 의 필수 필드 검증 — TEXT 는 content, IMAGE 는 imageKeys 가 있어야 함
	private void requireValidMessage(ChatMessageSendRequest request, Long roomId, Long senderId) {
		if (request.getMessageType() == null) {
			log.warn("메시지 타입 없이 전송을 시도함 roomId={} senderId={}", roomId, senderId);
			throw new CustomException(ChatErrorCode.CHAT_400_INVALID_MESSAGE);
		}
		if (request.getMessageType() == ChatMessageType.TEXT) {
			if (!StringUtils.hasText(request.getContent())) {
				log.warn("content 없이 TEXT 메시지 전송을 시도함 roomId={} senderId={}", roomId, senderId);
				throw new CustomException(ChatErrorCode.CHAT_400_INVALID_MESSAGE);
			}
		} else if (request.getImageKeys() == null || request.getImageKeys().isEmpty()) {
			log.warn("imageKeys 없이 IMAGE 메시지 전송을 시도함 roomId={} senderId={}", roomId, senderId);
			throw new CustomException(ChatErrorCode.CHAT_400_INVALID_MESSAGE);
		}
	}

	/*
	 * 발신자 본인에게는 항상 push 해 다중 기기를 동기화하고, 상대는 나간 방이 아닐 때만 보냄
	 * (나간 상대는 push·알림을 받아도 누르면 CHAT_403 인 막다른 길이라 건너뜀). 상대가 지금 이
	 * 방을 보고 있으면 바로 읽은 걸로 치고, 아니면 목록 갱신과 알림을 보냄.
	 */
	private void routeDelivery(
			ChatRoom room, Long roomId, User sender, User counterpart, ChatMessage message, ChatMessageResponse response) {
		chatWebSocketService.pushMessage(roomId, sender.getId(), response);

		ChatRoomMember counterpartMember = chatRoomMemberRepository
				.findByChatRoomIdAndUserId(roomId, counterpart.getId())
				.orElse(null);
		if (counterpartMember == null || counterpartMember.hasLeft()) {
			return;
		}

		chatWebSocketService.pushMessage(roomId, counterpart.getId(), response);

		// 상대가 지금 이 방을 보고 있으면 바로 읽은 걸로 침 — 목록 안읽음도 안 늘고 알림도 건너뜀
		if (presenceRegistry.isViewing(roomId, counterpart.getId())) {
			chatRoomMemberRepository.markReadIfNewer(counterpartMember.getId(), message, message.getId());
			ChatReadEventResponse readEvent = ChatReadEventResponse.builder()
					.roomId(roomId)
					.lastReadMessageId(message.getId())
					.build();
			chatWebSocketService.pushReadEvent(roomId, sender.getId(), readEvent);
			return;
		}

		Long lastReadId = counterpartMember.getLastReadMessage() != null
				? counterpartMember.getLastReadMessage().getId() : null;
		long unreadCount = chatMessageRepository.countUnread(roomId, counterpart.getId(), lastReadId);
		ChatRoomListUpdateResponse listUpdate = ChatRoomListUpdateResponse.builder()
				.roomId(roomId)
				.lastMessage(previewOf(message))
				.lastMessageAt(message.getCreatedAt())
				.unreadCount((int) unreadCount)
				.build();
		chatWebSocketService.pushRoomListUpdate(counterpart.getId(), listUpdate);

		// 알림 INSERT·설정 조회는 커밋 전에 하면 아직 안 끝난 트랜잭션에 묶여 들어감 — pushMessage 와 같은 패턴으로 커밋 후로 미룸
		afterCommit(() -> notificationService.notify(
				counterpart, NotificationSettingType.CHAT, NotificationCategory.ACTION_REQUIRED,
				room.getItem().getName(), "새 메시지가 왔어요.",
				room.getItem().getCoverImageKey(), NotificationTargetType.CHAT_ROOM, room.getId()));
	}

	// 트랜잭션이 끝까지 커밋된 뒤에만 실행함. 트랜잭션 없이 불리면(단위 테스트 등) 동기화가
	// 활성화돼 있지 않으니 그냥 바로 실행함 — ChatWebSocketService 와 같은 패턴
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

	// 방 구독(= 입장) 시점에 호출됨 — ChatRoomPresenceListener 전용. 그 방의 최신 메시지까지 한 번에 읽음 처리함
	@Transactional
	@Loggable("채팅방 구독 시 읽음 처리")
	public void markBacklogRead(Long userId, Long roomId) {
		ChatRoom room = requireRoom(roomId);
		ChatRoomMember member = requireActiveMember(roomId, userId);

		chatMessageRepository.findTopByChatRoomIdOrderByIdDesc(roomId).ifPresent(latest -> {
			chatRoomMemberRepository.markReadIfNewer(member.getId(), latest, latest.getId());
			User counterpart = counterpartOf(room, userId);
			ChatReadEventResponse event = ChatReadEventResponse.builder()
					.roomId(roomId)
					.lastReadMessageId(latest.getId())
					.build();
			chatWebSocketService.pushReadEvent(roomId, counterpart.getId(), event);
		});
	}

	// 읽음 처리 + 상대방에게 실시간 push
	@Transactional
	@Loggable("채팅 읽음 처리")
	public void read(Long userId, Long roomId, ChatReadRequest request) {
		// 방과 활성 멤버십을 확인해 권한 없는 요청을 막음
		ChatRoom room = requireRoom(roomId);
		ChatRoomMember member = requireActiveMember(roomId, userId);

		// 요청이 가리키는 메시지가 실제로 이 방의 것인지 확인하고 멤버의 읽음 위치를 갱신함
		ChatMessage lastReadMessage = requireMessageInRoom(request.getLastReadMessageId(), roomId);
		chatRoomMemberRepository.markReadIfNewer(member.getId(), lastReadMessage, lastReadMessage.getId());

		// 상대방에게만 읽음 이벤트를 보냄 — 읽음을 처리한 사람 본인은 이미 알고 있음
		User counterpart = counterpartOf(room, userId);
		ChatReadEventResponse event = ChatReadEventResponse.builder()
				.roomId(roomId)
				.lastReadMessageId(request.getLastReadMessageId())
				.build();
		chatWebSocketService.pushReadEvent(roomId, counterpart.getId(), event);
	}

	// 채팅방 나가기. 방·메시지는 남고 이 참여자의 leftAt 만 찍힘
	@Transactional
	@Loggable("채팅방 나가기")
	public void leave(Long userId, Long roomId) {
		requireRoom(roomId);
		ChatRoomMember member = requireActiveMember(roomId, userId);
		member.leave(LocalDateTime.now());
	}

	// ── 개설 보조 ───────────────────────────────────────────────

	// 판매중 아이템. listing 이 가격 출처 — 구매 제안·거래 의사와 무관하게 독립적으로 염
	private ChatRoom openForListing(Item item, User requester, LocalDateTime now) {
		ItemListing listing = itemListingRepository.findOpenByItemId(item.getId())
				.orElseThrow(() -> {
					log.warn("판매중인데 열린 판매 등록이 없는 아이템임 itemId={}", item.getId());
					return new CustomException(ItemErrorCode.ITEM_404);
				});
		User seller = listing.getSeller();
		// 판매자 기준으로 자기 상품·차단 관계를 확인함
		requireNotSelf(requester, seller);
		requireNotBlocked(requester, seller);

		ChatRoom room = findOrOpenRoom(item.getId(), requester, seller,
				() -> ChatRoom.openForListing(item, listing, requester, seller, now));
		// 미판매로 먼저 열렸던 방이 나중에 판매 전환되면 가격 출처를 listing 으로 옮김
		if (room.getListing() == null || !room.getListing().getId().equals(listing.getId())) {
			room.updateListingPointer(listing);
		}
		ensureMembers(room, requester, seller);
		return room;
	}

	// 미판매 아이템. offerAllowed 가 꺼져 있으면 애초에 열 수 없음
	private ChatRoom openForUnsold(Item item, User requester, LocalDateTime now) {
		// 현재 소유자를 구함 — 이후 자기 상품·오퍼 허용·차단 확인에 전부 씀
		User seller = currentOwner(item.getId());
		requireNotSelf(requester, seller);

		if (!Boolean.TRUE.equals(item.getOfferAllowed())) {
			log.warn("구매 제안을 받지 않는 미판매 상품에 채팅을 시도함 itemId={}", item.getId());
			throw new CustomException(ChatErrorCode.CHAT_400_NOT_OPENABLE);
		}

		requireNotBlocked(requester, seller);

		ChatRoom room = findOrOpenRoom(item.getId(), requester, seller,
				() -> ChatRoom.openForUnsold(item, requester, seller, now));
		ensureMembers(room, requester, seller);
		return room;
	}

	/*
	 * 조회 → 없으면 생성. 조회와 생성 사이에 동시 요청이 끼면(더블탭·재시도) 둘 다 빈 조회를
	 * 보고 둘 다 저장을 시도해 uk_chat_room 을 위반할 수 있음 — 여기서는 잡지 않고 그대로 던짐.
	 * 지금 걸린 트랜잭션은 이미 rollback-only 로 못 박혀서 잡고 이어 써도 커밋 시점에 터지므로,
	 * 재시도는 완전히 새 트랜잭션에서 하도록 attemptOpen() 을 부른 open() 쪽에 맡김.
	 */
	private ChatRoom findOrOpenRoom(Long itemId, User buyer, User seller, Supplier<ChatRoom> factory) {
		return chatRoomRepository.findByItemIdAndBuyerIdAndSellerId(itemId, buyer.getId(), seller.getId())
				.orElseGet(() -> chatRoomRepository.save(factory.get()));
	}

	/*
	 * 멤버가 없으면 만들고, 있으면(나갔었어도) 참여 상태로 되돌림 — 아이템 상세에서
	 * '채팅하기'를 다시 누르는 것 자체가 재가입 의사표시임.
	 */
	private void ensureMembers(ChatRoom room, User buyer, User seller) {
		ensureMember(room, buyer);
		ensureMember(room, seller);
	}

	// 유니크 제약 위반 시 여기서 잡지 않는 이유는 findOrOpenRoom 의 주석과 같음 — 재시도는 open() 이 새 트랜잭션으로 함
	private void ensureMember(ChatRoom room, User user) {
		ChatRoomMember member = chatRoomMemberRepository.findByChatRoomIdAndUserId(room.getId(), user.getId())
				.orElseGet(() -> chatRoomMemberRepository.save(ChatRoomMember.join(room, user)));
		member.rejoin();
	}

	// ── 헤더 CTA ───────────────────────────────────────────────

	// 좌측 버튼 — 판매 상태 × 역할로만 갈림. 항상 활성
	private ChatCtaResponse buildLeftCta(Item item, boolean isBuyer) {
		boolean onSale = item.getSaleStatus() == SaleStatus.ON_SALE;
		String label = onSale
				? (isBuyer ? "상품 상세" : "상품 관리")
				: (isBuyer ? "아이템 상세" : "아이템 관리");
		ChatCtaAction action = isBuyer ? ChatCtaAction.ITEM_DETAIL : ChatCtaAction.ITEM_MANAGE;
		return ChatCtaResponse.builder().label(label).action(action).targetId(item.getId()).enabled(true).build();
	}

	// 우측 버튼 — 관련 제안의 거래 단계에 따라 갈림(제안 전/응답 대기/거래 생성 후/종료)
	private ChatCtaResponse buildRightCta(Item item, boolean isBuyer, PurchaseOffer offer, TradeOrder order) {
		// 관련 제안이 아예 없으면 아직 '제안 전' 단계임
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
				// 미판매+구매제안만 offerAllowed 전환에 영향을 받음 — 거래 의사는 늘 활성임
				boolean enabled = isTradeIntent || Boolean.TRUE.equals(item.getOfferAllowed());
				yield ChatCtaResponse.builder()
						.label(label).action(ChatCtaAction.OFFER_DETAIL).targetId(offer.getId()).enabled(enabled).build();
			}
			case ACCEPTED -> ChatCtaResponse.builder()
					.label("거래 상세").action(ChatCtaAction.ORDER_DETAIL)
					.targetId(order != null ? order.getId() : null).enabled(true).build();
			// 거절·취소·만료는 같은 제안으로는 더 못 들어감 — 비활성으로 종료 상태를 표시함
			case REJECTED, CANCELED, EXPIRED -> ChatCtaResponse.builder()
					.label("제안 종료됨").action(ChatCtaAction.OFFER_DETAIL).targetId(offer.getId()).enabled(false).build();
		};
	}

	// 이 방과 관련된 제안. 미판매 방은 chat_room.purchase_offer_id, 판매중 방은 최신 제안을 따로 찾음
	private PurchaseOffer resolveRelevantOffer(ChatRoom room) {
		if (room.getPurchaseOffer() != null) {
			return room.getPurchaseOffer();
		}
		return purchaseOfferRepository
				.findLatestByItemIdAndBuyerId(room.getItem().getId(), room.getBuyer().getId())
				.orElse(null);
	}

	// listing 이 있으면 그 가격, 없으면 관련 제안 금액, 둘 다 없으면(제안 전) null
	private String resolvePrice(ChatRoom room, PurchaseOffer offer) {
		if (room.getListing() != null) {
			return String.valueOf(room.getListing().getPrice());
		}
		return offer != null ? String.valueOf(offer.getOfferPrice()) : null;
	}

	// ── 메시지 보조 ───────────────────────────────────────────────

	// 요청의 imageKeys 순서를 그대로 sortOrder 로 저장함 — 말풍선 안의 노출 순서가 됨
	private List<String> saveImages(ChatMessage message, List<String> imageKeys) {
		List<ChatMessageImage> images = new ArrayList<>();
		for (int i = 0; i < imageKeys.size(); i++) {
			images.add(ChatMessageImage.of(message, imageKeys.get(i), i));
		}
		chatMessageImageRepository.saveAll(images);
		return images.stream().map(img -> fileStorage.toUrl(img.getImageKey())).toList();
	}

	// IMAGE 메시지 id 들만 모아 한 번에 사진을 읽고, 메시지 id 별로 묶어 돌려줌
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

	// 메시지 타입과 안 맞는 필드는 null 로 비워 내려보냄 — TEXT 는 images, IMAGE 는 content 를 안 씀
	private ChatMessageResponse toMessageResponse(ChatMessage message, Map<Long, List<String>> imagesByMessage) {
		String content = message.getMessageType() == ChatMessageType.TEXT ? message.getContent() : null;
		List<String> images = message.getMessageType() == ChatMessageType.IMAGE
				? imagesByMessage.getOrDefault(message.getId(), List.of())
				: null;
		return chatMapper.toMessageResponse(message, content, images);
	}

	// ── 목록 보조 ───────────────────────────────────────────────

	// wornDates·latestMessages 는 list() 가 배치로 미리 조회해 넘긴 맵 — 행마다 쿼리하지 않음
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
				chatMapper.toListItemSummaryResponse(item),
				wornDates.get(item.getId()));
	}

	// 사진 메시지는 미리보기 문구로 대신함 — 한 말풍선에 여러 장이라 URL 하나로 대표할 수 없음
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

	// 본인이 올린 상품에는 채팅을 열 수 없음
	private void requireNotSelf(User requester, User seller) {
		if (requester.getId().equals(seller.getId())) {
			log.warn("본인 상품에 채팅을 시도함 userId={}", requester.getId());
			throw new CustomException(ChatErrorCode.CHAT_400_SELF);
		}
	}

	// 차단은 양방향으로 확인함 — 내가 차단했든 상대가 차단했든 채팅은 막힘
	private void requireNotBlocked(User a, User b) {
		if (isBlocked(a, b)) {
			log.warn("차단 관계인 상대와 채팅을 시도함 userId={} counterpartId={}", a.getId(), b.getId());
			throw new CustomException(ChatErrorCode.CHAT_403_BLOCKED);
		}
	}

	private boolean isBlocked(User a, User b) {
		return userBlockRepository.existsMutualBlock(a.getId(), b.getId());
	}

	// fetch join 없는 가벼운 조회 — 연관 엔티티까지 필요 없는 곳에서 씀
	private ChatRoom requireRoom(Long roomId) {
		return chatRoomRepository.findById(roomId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 채팅방임 roomId={}", roomId);
					return new CustomException(ChatErrorCode.CHAT_404_ROOM);
				});
	}

	private ChatRoom requireRoomDetail(Long roomId) {
		return chatRoomRepository.findDetailById(roomId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 채팅방임 roomId={}", roomId);
					return new CustomException(ChatErrorCode.CHAT_404_ROOM);
				});
	}

	private ChatRoomMember requireActiveMember(Long roomId, Long userId) {
		ChatRoomMember member = chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
				.orElseThrow(() -> {
					log.warn("채팅방 멤버가 아님 roomId={} userId={}", roomId, userId);
					return new CustomException(ChatErrorCode.CHAT_403);
				});
		if (member.hasLeft()) {
			log.warn("나간 채팅방에 접근을 시도함 roomId={} userId={}", roomId, userId);
			throw new CustomException(ChatErrorCode.CHAT_403);
		}
		return member;
	}

	// 읽음 처리 대상 메시지. 다른 방의 메시지 id 를 보내는 입력 오류를 막음
	private ChatMessage requireMessageInRoom(Long messageId, Long roomId) {
		ChatMessage message = chatMessageRepository.findById(messageId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 메시지로 읽음 처리를 시도함 messageId={}", messageId);
					return new CustomException(CommonErrorCode.BAD_REQUEST);
				});
		if (!message.getChatRoom().getId().equals(roomId)) {
			log.warn("다른 방의 메시지로 읽음 처리를 시도함 messageId={} roomId={}", messageId, roomId);
			throw new CustomException(CommonErrorCode.BAD_REQUEST);
		}
		return message;
	}

	private Item findActiveItem(Long itemId) {
		return itemRepository.findActive(itemId)
				.orElseThrow(() -> {
					log.warn("없거나 삭제된 아이템임 itemId={}", itemId);
					return new CustomException(ItemErrorCode.ITEM_404);
				});
	}

	private User currentOwner(Long itemId) {
		return itemOwnershipRepository.findCurrent(itemId)
				.map(ItemOwnership::getOwner)
				.orElseThrow(() -> {
					log.warn("소유 구간이 없는 아이템임 itemId={}", itemId);
					return new CustomException(ItemErrorCode.ITEM_404);
				});
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> {
					log.warn("없는 사용자임 userId={}", userId);
					return new CustomException(UserErrorCode.USER_404);
				});
	}

	private Map<Long, LocalDate> lastWornDatesOf(Collection<Long> itemIds) {
		if (itemIds.isEmpty()) {
			return Map.of();
		}
		return itemWearRecordRepository.findLastWornDates(itemIds).stream()
				.collect(Collectors.toMap(ItemLastWornDateRow::itemId, ItemLastWornDateRow::wornDate));
	}
}
