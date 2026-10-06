package com.tenure.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.tenure.domain.address.entity.ShippingAddress;
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
import com.tenure.domain.chat.mapper.ChatMapperImpl;
import com.tenure.domain.chat.repository.ChatMessageImageRepository;
import com.tenure.domain.chat.repository.ChatMessageRepository;
import com.tenure.domain.chat.repository.ChatRoomFeedRow;
import com.tenure.domain.chat.repository.ChatRoomMemberRepository;
import com.tenure.domain.chat.repository.ChatRoomRepository;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.item.repository.ItemWearRecordRepository;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.domain.notification.enums.NotificationSettingType;
import com.tenure.domain.notification.enums.NotificationTargetType;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.domain.offer.entity.PurchaseOffer;
import com.tenure.domain.offer.enums.OfferCancelReason;
import com.tenure.domain.offer.enums.OfferType;
import com.tenure.domain.offer.repository.PurchaseOfferRepository;
import com.tenure.domain.order.entity.TradeOrder;
import com.tenure.domain.order.repository.TradeOrderRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.enums.UserStatus;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CommonErrorCode;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.storage.FileStorage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatService")
class ChatServiceTest {

	private static final Long BUYER_ID = 1L;
	private static final Long SELLER_ID = 2L;
	private static final Long OTHER_ID = 3L;
	private static final Long ROOM_ID = 100L;
	private static final Long ITEM_ID = 200L;
	private static final Long OFFER_ID = 300L;
	private static final Long ORDER_ID = 400L;

	@Mock private ChatRoomRepository chatRoomRepository;
	@Mock private ChatRoomMemberRepository chatRoomMemberRepository;
	@Mock private ChatMessageRepository chatMessageRepository;
	@Mock private ChatMessageImageRepository chatMessageImageRepository;
	@Mock private ItemRepository itemRepository;
	@Mock private ItemListingRepository itemListingRepository;
	@Mock private ItemOwnershipRepository itemOwnershipRepository;
	@Mock private ItemWearRecordRepository itemWearRecordRepository;
	@Mock private PurchaseOfferRepository purchaseOfferRepository;
	@Mock private TradeOrderRepository tradeOrderRepository;
	@Mock private UserBlockRepository userBlockRepository;
	@Mock private UserRepository userRepository;
	@Mock private NotificationService notificationService;
	@Mock private FileStorage fileStorage;
	@Mock private ChatWebSocketService chatWebSocketService;

	// 매핑은 검증 대상이라 mock 이 아니라 실제 생성 구현을 넣는다
	@Spy private ChatMapper chatMapper = new ChatMapperImpl();

	@InjectMocks private ChatService chatService;

	@Nested
	@DisplayName("채팅방 개설")
	class Open {

		@Test
		@DisplayName("판매중 아이템이면 listing 의 판매자와 방을 연다")
		void open_onSaleItem_opensViaListing() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);

			given(itemRepository.findActive(ITEM_ID)).willReturn(Optional.of(item));
			given(userRepository.findById(BUYER_ID)).willReturn(Optional.of(buyer));
			given(itemListingRepository.findOpenByItemId(ITEM_ID)).willReturn(Optional.of(listing));
			given(userBlockRepository.existsBlock(anyLong(), anyLong())).willReturn(false);
			given(chatRoomRepository.findByItemIdAndBuyerIdAndSellerId(ITEM_ID, BUYER_ID, SELLER_ID))
					.willReturn(Optional.empty());
			given(chatRoomRepository.save(any(ChatRoom.class))).willAnswer(invocation -> {
				ChatRoom saved = invocation.getArgument(0);
				ReflectionTestUtils.setField(saved, "id", ROOM_ID);
				return saved;
			});
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID)).willReturn(Optional.empty());
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, SELLER_ID)).willReturn(Optional.empty());
			given(chatRoomMemberRepository.save(any(ChatRoomMember.class))).willAnswer(invocation -> invocation.getArgument(0));

			ChatRoomOpenResponse response = chatService.open(BUYER_ID, ITEM_ID);

			assertThat(response.getRoomId()).isEqualTo(ROOM_ID);
			verify(chatRoomRepository).save(any(ChatRoom.class));
			verify(chatRoomMemberRepository, times(2)).save(any(ChatRoomMember.class));
		}

		@Test
		@DisplayName("미판매+제안허용 아이템이면 현재 소유자와 방을 연다")
		void open_unsoldOfferAllowedItem_opensWithoutListing() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.NOT_FOR_SALE, true);
			ItemOwnership ownership = ItemOwnership.openInitial(item, seller, LocalDate.of(2025, 2, 8));

			given(itemRepository.findActive(ITEM_ID)).willReturn(Optional.of(item));
			given(userRepository.findById(BUYER_ID)).willReturn(Optional.of(buyer));
			given(itemOwnershipRepository.findCurrent(ITEM_ID)).willReturn(Optional.of(ownership));
			given(userBlockRepository.existsBlock(anyLong(), anyLong())).willReturn(false);
			given(chatRoomRepository.findByItemIdAndBuyerIdAndSellerId(ITEM_ID, BUYER_ID, SELLER_ID))
					.willReturn(Optional.empty());
			given(chatRoomRepository.save(any(ChatRoom.class))).willAnswer(invocation -> {
				ChatRoom saved = invocation.getArgument(0);
				ReflectionTestUtils.setField(saved, "id", ROOM_ID);
				return saved;
			});
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID)).willReturn(Optional.empty());
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, SELLER_ID)).willReturn(Optional.empty());
			given(chatRoomMemberRepository.save(any(ChatRoomMember.class))).willAnswer(invocation -> invocation.getArgument(0));

			ChatRoomOpenResponse response = chatService.open(BUYER_ID, ITEM_ID);

			assertThat(response.getRoomId()).isEqualTo(ROOM_ID);
			verify(chatRoomRepository).save(any(ChatRoom.class));
			verify(chatRoomMemberRepository, times(2)).save(any(ChatRoomMember.class));
		}

		@Test
		@DisplayName("미판매+제안비허용 아이템이면 CHAT_400_NOT_OPENABLE 이다")
		void open_unsoldOfferNotAllowedItem_throwsNotOpenable() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.NOT_FOR_SALE, false);
			ItemOwnership ownership = ItemOwnership.openInitial(item, seller, LocalDate.of(2025, 2, 8));

			given(itemRepository.findActive(ITEM_ID)).willReturn(Optional.of(item));
			given(userRepository.findById(BUYER_ID)).willReturn(Optional.of(buyer));
			given(itemOwnershipRepository.findCurrent(ITEM_ID)).willReturn(Optional.of(ownership));

			assertThatThrownBy(() -> chatService.open(BUYER_ID, ITEM_ID))
					.isInstanceOf(CustomException.class)
					.extracting(ChatServiceTest::errorCodeOf)
					.isEqualTo(ChatErrorCode.CHAT_400_NOT_OPENABLE);

			verify(chatRoomRepository, never()).save(any());
		}

		@Test
		@DisplayName("본인 상품에 채팅방을 열려고 하면 CHAT_400_SELF 다")
		void open_selfItem_throwsSelf() {
			User requester = user(BUYER_ID);
			Item item = item(ITEM_ID, requester, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, requester, 360000);

			given(itemRepository.findActive(ITEM_ID)).willReturn(Optional.of(item));
			given(userRepository.findById(BUYER_ID)).willReturn(Optional.of(requester));
			given(itemListingRepository.findOpenByItemId(ITEM_ID)).willReturn(Optional.of(listing));

			assertThatThrownBy(() -> chatService.open(BUYER_ID, ITEM_ID))
					.isInstanceOf(CustomException.class)
					.extracting(ChatServiceTest::errorCodeOf)
					.isEqualTo(ChatErrorCode.CHAT_400_SELF);

			verify(chatRoomRepository, never()).save(any());
		}

		@Test
		@DisplayName("차단 관계면 CHAT_403_BLOCKED 다")
		void open_blockedRelationship_throwsBlocked() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);

			given(itemRepository.findActive(ITEM_ID)).willReturn(Optional.of(item));
			given(userRepository.findById(BUYER_ID)).willReturn(Optional.of(buyer));
			given(itemListingRepository.findOpenByItemId(ITEM_ID)).willReturn(Optional.of(listing));
			given(userBlockRepository.existsBlock(BUYER_ID, SELLER_ID)).willReturn(true);

			assertThatThrownBy(() -> chatService.open(BUYER_ID, ITEM_ID))
					.isInstanceOf(CustomException.class)
					.extracting(ChatServiceTest::errorCodeOf)
					.isEqualTo(ChatErrorCode.CHAT_403_BLOCKED);

			verify(chatRoomRepository, never()).save(any());
		}

		@Test
		@DisplayName("이미 있는 방이면 기존 방 id 를 그대로 돌려주고 멤버를 다시 만들지 않는다")
		void open_existingRoom_returnsSameRoomIdempotentlyWithoutDuplicateMembers() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom existingRoom = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(existingRoom, "id", ROOM_ID);

			given(itemRepository.findActive(ITEM_ID)).willReturn(Optional.of(item));
			given(userRepository.findById(BUYER_ID)).willReturn(Optional.of(buyer));
			given(itemListingRepository.findOpenByItemId(ITEM_ID)).willReturn(Optional.of(listing));
			given(userBlockRepository.existsBlock(anyLong(), anyLong())).willReturn(false);
			given(chatRoomRepository.findByItemIdAndBuyerIdAndSellerId(ITEM_ID, BUYER_ID, SELLER_ID))
					.willReturn(Optional.of(existingRoom));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(existingRoom, buyer)));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, SELLER_ID))
					.willReturn(Optional.of(member(existingRoom, seller)));

			ChatRoomOpenResponse response = chatService.open(BUYER_ID, ITEM_ID);

			assertThat(response.getRoomId()).isEqualTo(ROOM_ID);
			verify(chatRoomRepository, never()).save(any());
			verify(chatRoomMemberRepository, never()).save(any());
		}

		@Test
		@DisplayName("나갔던 사용자가 다시 열면 재가입된다(leftAt 해제)")
		void open_existingRoomLeftByRequester_rejoins() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom existingRoom = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(existingRoom, "id", ROOM_ID);
			ChatRoomMember leftBuyer = member(existingRoom, buyer);
			leftBuyer.leave(LocalDateTime.now());

			given(itemRepository.findActive(ITEM_ID)).willReturn(Optional.of(item));
			given(userRepository.findById(BUYER_ID)).willReturn(Optional.of(buyer));
			given(itemListingRepository.findOpenByItemId(ITEM_ID)).willReturn(Optional.of(listing));
			given(userBlockRepository.existsBlock(anyLong(), anyLong())).willReturn(false);
			given(chatRoomRepository.findByItemIdAndBuyerIdAndSellerId(ITEM_ID, BUYER_ID, SELLER_ID))
					.willReturn(Optional.of(existingRoom));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(leftBuyer));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, SELLER_ID))
					.willReturn(Optional.of(member(existingRoom, seller)));

			ChatRoomOpenResponse response = chatService.open(BUYER_ID, ITEM_ID);

			assertThat(response.getRoomId()).isEqualTo(ROOM_ID);
			assertThat(leftBuyer.hasLeft()).isFalse();
		}

		@Test
		@DisplayName("동시 요청으로 유니크 제약을 위반해도 그새 만들어진 방을 찾아 돌려준다")
		void open_concurrentRequestViolatesUniqueConstraint_fallsBackToExistingRoom() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom roomCreatedByOtherRequest = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(roomCreatedByOtherRequest, "id", ROOM_ID);

			given(itemRepository.findActive(ITEM_ID)).willReturn(Optional.of(item));
			given(userRepository.findById(BUYER_ID)).willReturn(Optional.of(buyer));
			given(itemListingRepository.findOpenByItemId(ITEM_ID)).willReturn(Optional.of(listing));
			given(userBlockRepository.existsBlock(anyLong(), anyLong())).willReturn(false);
			given(chatRoomRepository.findByItemIdAndBuyerIdAndSellerId(ITEM_ID, BUYER_ID, SELLER_ID))
					.willReturn(Optional.empty(), Optional.of(roomCreatedByOtherRequest));
			given(chatRoomRepository.save(any(ChatRoom.class)))
					.willThrow(new DataIntegrityViolationException("uk_chat_room"));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(roomCreatedByOtherRequest, buyer)));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, SELLER_ID))
					.willReturn(Optional.of(member(roomCreatedByOtherRequest, seller)));

			ChatRoomOpenResponse response = chatService.open(BUYER_ID, ITEM_ID);

			assertThat(response.getRoomId()).isEqualTo(ROOM_ID);
		}

		@Test
		@DisplayName("미판매로 열렸던 방이 나중에 판매 전환되면 listing 포인터가 갱신된다")
		void open_existingRoomWithoutListing_updatesListingPointerOnSaleConversion() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom existingRoom = ChatRoom.openForUnsold(item, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(existingRoom, "id", ROOM_ID);

			given(itemRepository.findActive(ITEM_ID)).willReturn(Optional.of(item));
			given(userRepository.findById(BUYER_ID)).willReturn(Optional.of(buyer));
			given(itemListingRepository.findOpenByItemId(ITEM_ID)).willReturn(Optional.of(listing));
			given(userBlockRepository.existsBlock(anyLong(), anyLong())).willReturn(false);
			given(chatRoomRepository.findByItemIdAndBuyerIdAndSellerId(ITEM_ID, BUYER_ID, SELLER_ID))
					.willReturn(Optional.of(existingRoom));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(existingRoom, buyer)));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, SELLER_ID))
					.willReturn(Optional.of(member(existingRoom, seller)));

			chatService.open(BUYER_ID, ITEM_ID);

			assertThat(existingRoom.getListing()).isEqualTo(listing);
		}
	}

	@Nested
	@DisplayName("채팅방 헤더")
	class Header {

		@Test
		@DisplayName("관련 제안이 없으면 우측 버튼은 '구매하기' 이고 좌측은 판매중+구매자 기준 문구다")
		void header_noRelatedOffer_showsPurchaseCta() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			given(chatRoomRepository.findDetailById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(purchaseOfferRepository.findLatestByItemIdAndBuyerId(ITEM_ID, BUYER_ID)).willReturn(Optional.empty());
			given(itemWearRecordRepository.findLastWornDates(List.of(ITEM_ID))).willReturn(List.of());

			ChatRoomHeaderResponse response = chatService.header(BUYER_ID, ROOM_ID);

			assertThat(response.getOrderId()).isNull();
			assertThat(response.getPrice()).isEqualTo("360000");
			List<ChatCtaResponse> ctas = response.getCtas();
			assertThat(ctas).hasSize(2);
			assertThat(ctas.get(0).getLabel()).isEqualTo("상품 상세");
			assertThat(ctas.get(0).getAction()).isEqualTo(ChatCtaAction.ITEM_DETAIL);
			assertThat(ctas.get(1).getLabel()).isEqualTo("구매하기");
			assertThat(ctas.get(1).getAction()).isEqualTo(ChatCtaAction.PURCHASE);
			assertThat(ctas.get(1).getTargetId()).isEqualTo(ITEM_ID);
			assertThat(ctas.get(1).getEnabled()).isTrue();
		}

		@Test
		@DisplayName("탈퇴한 상대는 닉네임이 '탈퇴한 사용자' 로 내려간다")
		void header_withdrawnCounterpart_showsWithdrawnNickname() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			ReflectionTestUtils.setField(seller, "status", UserStatus.WITHDRAWN);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			given(chatRoomRepository.findDetailById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(purchaseOfferRepository.findLatestByItemIdAndBuyerId(ITEM_ID, BUYER_ID)).willReturn(Optional.empty());
			given(itemWearRecordRepository.findLastWornDates(List.of(ITEM_ID))).willReturn(List.of());

			ChatRoomHeaderResponse response = chatService.header(BUYER_ID, ROOM_ID);

			assertThat(response.getCounterpart().getNickname()).isEqualTo("탈퇴한 사용자");
		}

		@Test
		@DisplayName("판매중 방이어도 최근 제안이 있으면 그 제안을 기준으로 CTA 를 만든다")
		void header_onSaleRoomWithPendingOffer_usesRepositoryFallback() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, true);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);
			PurchaseOffer pendingOffer = offer(OFFER_ID, item, buyer, seller, OfferType.PURCHASE_OFFER, 350000);

			given(chatRoomRepository.findDetailById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(purchaseOfferRepository.findLatestByItemIdAndBuyerId(ITEM_ID, BUYER_ID))
					.willReturn(Optional.of(pendingOffer));
			given(itemWearRecordRepository.findLastWornDates(List.of(ITEM_ID))).willReturn(List.of());

			ChatRoomHeaderResponse response = chatService.header(BUYER_ID, ROOM_ID);

			assertThat(response.getCtas().get(1).getLabel()).isEqualTo("보낸 제안 보기");
			verify(purchaseOfferRepository).findLatestByItemIdAndBuyerId(ITEM_ID, BUYER_ID);
		}

		@Test
		@DisplayName("대기중 구매 제안은 구매자/판매자에게 다른 문구를 보여준다")
		void header_pendingPurchaseOfferAllowed_showsRoleBasedLabels() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.NOT_FOR_SALE, true);
			PurchaseOffer pendingOffer = offer(OFFER_ID, item, buyer, seller, OfferType.PURCHASE_OFFER, 350000);
			ChatRoom room = ChatRoom.openForOffer(item, buyer, seller, pendingOffer, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			given(chatRoomRepository.findDetailById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, SELLER_ID))
					.willReturn(Optional.of(member(room, seller)));
			given(itemWearRecordRepository.findLastWornDates(List.of(ITEM_ID))).willReturn(List.of());

			ChatCtaResponse buyerCta = chatService.header(BUYER_ID, ROOM_ID).getCtas().get(1);
			ChatCtaResponse sellerCta = chatService.header(SELLER_ID, ROOM_ID).getCtas().get(1);

			assertThat(buyerCta.getLabel()).isEqualTo("보낸 제안 보기");
			assertThat(buyerCta.getEnabled()).isTrue();
			assertThat(sellerCta.getLabel()).isEqualTo("제안 확인");
			assertThat(sellerCta.getEnabled()).isTrue();
		}

		@Test
		@DisplayName("대기중 거래 의사는 구매자/판매자에게 다른 문구를 보여준다")
		void header_pendingTradeIntent_showsRoleBasedLabels() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, true);
			PurchaseOffer pendingOffer = offer(OFFER_ID, item, buyer, seller, OfferType.TRADE_INTENT, 350000);
			ChatRoom room = ChatRoom.openForOffer(item, buyer, seller, pendingOffer, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			given(chatRoomRepository.findDetailById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, SELLER_ID))
					.willReturn(Optional.of(member(room, seller)));
			given(itemWearRecordRepository.findLastWornDates(List.of(ITEM_ID))).willReturn(List.of());

			ChatCtaResponse buyerCta = chatService.header(BUYER_ID, ROOM_ID).getCtas().get(1);
			ChatCtaResponse sellerCta = chatService.header(SELLER_ID, ROOM_ID).getCtas().get(1);

			assertThat(buyerCta.getLabel()).isEqualTo("보낸 요청 보기");
			assertThat(sellerCta.getLabel()).isEqualTo("요청 확인");
		}

		@Test
		@DisplayName("미판매+구매제안은 offerAllowed 가 꺼지면 비활성이다")
		void header_pendingPurchaseOffer_offerNotAllowed_disabled() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.NOT_FOR_SALE, false);
			PurchaseOffer pendingOffer = offer(OFFER_ID, item, buyer, seller, OfferType.PURCHASE_OFFER, 350000);
			ChatRoom room = ChatRoom.openForOffer(item, buyer, seller, pendingOffer, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			given(chatRoomRepository.findDetailById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(itemWearRecordRepository.findLastWornDates(List.of(ITEM_ID))).willReturn(List.of());

			ChatCtaResponse cta = chatService.header(BUYER_ID, ROOM_ID).getCtas().get(1);

			assertThat(cta.getLabel()).isEqualTo("보낸 제안 보기");
			assertThat(cta.getEnabled()).isFalse();
		}

		@Test
		@DisplayName("거래 의사는 offerAllowed 가 꺼져도 그대로 활성이다")
		void header_pendingTradeIntent_offerNotAllowed_stillEnabled() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			PurchaseOffer pendingOffer = offer(OFFER_ID, item, buyer, seller, OfferType.TRADE_INTENT, 350000);
			ChatRoom room = ChatRoom.openForOffer(item, buyer, seller, pendingOffer, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			given(chatRoomRepository.findDetailById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(itemWearRecordRepository.findLastWornDates(List.of(ITEM_ID))).willReturn(List.of());

			ChatCtaResponse cta = chatService.header(BUYER_ID, ROOM_ID).getCtas().get(1);

			assertThat(cta.getEnabled()).isTrue();
		}

		@Test
		@DisplayName("수락된 제안은 거래 상세로 연결되고 orderId 가 채워진다")
		void header_acceptedOffer_showsOrderDetailWithOrderId() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.NOT_FOR_SALE, true);
			PurchaseOffer offer = offer(OFFER_ID, item, buyer, seller, OfferType.PURCHASE_OFFER, 350000);
			offer.accept(LocalDateTime.now());
			ChatRoom room = ChatRoom.openForOffer(item, buyer, seller, offer, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);
			TradeOrder order = order(offer, ORDER_ID);

			given(chatRoomRepository.findDetailById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(tradeOrderRepository.findByOfferId(OFFER_ID)).willReturn(Optional.of(order));
			given(itemWearRecordRepository.findLastWornDates(List.of(ITEM_ID))).willReturn(List.of());

			ChatRoomHeaderResponse response = chatService.header(BUYER_ID, ROOM_ID);

			assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
			ChatCtaResponse cta = response.getCtas().get(1);
			assertThat(cta.getLabel()).isEqualTo("거래 상세");
			assertThat(cta.getAction()).isEqualTo(ChatCtaAction.ORDER_DETAIL);
			assertThat(cta.getTargetId()).isEqualTo(ORDER_ID);
			assertThat(cta.getEnabled()).isTrue();
		}

		@Test
		@DisplayName("거절·취소·만료된 제안은 '제안 종료됨' 으로 비활성이다")
		void header_closedOffers_showClosedAndDisabled() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.NOT_FOR_SALE, true);

			assertClosedOffer(item, buyer, seller, 301L, 901L, o -> o.reject(LocalDateTime.now()));
			assertClosedOffer(item, buyer, seller, 302L, 902L,
					o -> o.cancel(OfferCancelReason.BUYER_CANCELED, LocalDateTime.now()));
			assertClosedOffer(item, buyer, seller, 303L, 903L, o -> o.expire(LocalDateTime.now()));
		}

		private void assertClosedOffer(
				Item item, User buyer, User seller, Long offerId, Long roomId, Consumer<PurchaseOffer> transition
		) {
			PurchaseOffer offer = offer(offerId, item, buyer, seller, OfferType.PURCHASE_OFFER, 350000);
			transition.accept(offer);
			ChatRoom room = ChatRoom.openForOffer(item, buyer, seller, offer, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", roomId);

			given(chatRoomRepository.findDetailById(roomId)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(itemWearRecordRepository.findLastWornDates(List.of(ITEM_ID))).willReturn(List.of());

			ChatCtaResponse cta = chatService.header(BUYER_ID, roomId).getCtas().get(1);

			assertThat(cta.getLabel()).isEqualTo("제안 종료됨");
			assertThat(cta.getAction()).isEqualTo(ChatCtaAction.OFFER_DETAIL);
			assertThat(cta.getTargetId()).isEqualTo(offerId);
			assertThat(cta.getEnabled()).isFalse();
		}

		@Test
		@DisplayName("좌측 버튼은 판매상태와 역할에 따라 문구가 달라진다")
		void header_leftCta_variesBySaleStatusAndRole() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);

			Item onSaleItem = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, onSaleItem, seller, 360000);
			ChatRoom onSaleRoom = ChatRoom.openForListing(onSaleItem, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(onSaleRoom, "id", ROOM_ID);

			given(chatRoomRepository.findDetailById(ROOM_ID)).willReturn(Optional.of(onSaleRoom));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(onSaleRoom, buyer)));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, SELLER_ID))
					.willReturn(Optional.of(member(onSaleRoom, seller)));
			given(purchaseOfferRepository.findLatestByItemIdAndBuyerId(ITEM_ID, BUYER_ID)).willReturn(Optional.empty());
			given(itemWearRecordRepository.findLastWornDates(List.of(ITEM_ID))).willReturn(List.of());

			ChatCtaResponse onSaleBuyerLeft = chatService.header(BUYER_ID, ROOM_ID).getCtas().get(0);
			ChatCtaResponse onSaleSellerLeft = chatService.header(SELLER_ID, ROOM_ID).getCtas().get(0);

			assertThat(onSaleBuyerLeft.getLabel()).isEqualTo("상품 상세");
			assertThat(onSaleBuyerLeft.getAction()).isEqualTo(ChatCtaAction.ITEM_DETAIL);
			assertThat(onSaleSellerLeft.getLabel()).isEqualTo("상품 관리");
			assertThat(onSaleSellerLeft.getAction()).isEqualTo(ChatCtaAction.ITEM_MANAGE);

			Long notForSaleRoomId = 910L;
			Long notForSaleItemId = 201L;
			Item notForSaleItem = item(notForSaleItemId, seller, SaleStatus.NOT_FOR_SALE, true);
			ChatRoom notForSaleRoom = ChatRoom.openForUnsold(notForSaleItem, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(notForSaleRoom, "id", notForSaleRoomId);

			given(chatRoomRepository.findDetailById(notForSaleRoomId)).willReturn(Optional.of(notForSaleRoom));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(notForSaleRoomId, BUYER_ID))
					.willReturn(Optional.of(member(notForSaleRoom, buyer)));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(notForSaleRoomId, SELLER_ID))
					.willReturn(Optional.of(member(notForSaleRoom, seller)));
			given(purchaseOfferRepository.findLatestByItemIdAndBuyerId(notForSaleItemId, BUYER_ID))
					.willReturn(Optional.empty());
			given(itemWearRecordRepository.findLastWornDates(List.of(notForSaleItemId))).willReturn(List.of());

			ChatCtaResponse notForSaleBuyerLeft = chatService.header(BUYER_ID, notForSaleRoomId).getCtas().get(0);
			ChatCtaResponse notForSaleSellerLeft = chatService.header(SELLER_ID, notForSaleRoomId).getCtas().get(0);

			assertThat(notForSaleBuyerLeft.getLabel()).isEqualTo("아이템 상세");
			assertThat(notForSaleSellerLeft.getLabel()).isEqualTo("아이템 관리");
		}

		@Test
		@DisplayName("방이 없으면 CHAT_404_ROOM 이다")
		void header_unknownRoom_notFound() {
			given(chatRoomRepository.findDetailById(ROOM_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> chatService.header(BUYER_ID, ROOM_ID))
					.isInstanceOf(CustomException.class)
					.extracting(ChatServiceTest::errorCodeOf)
					.isEqualTo(ChatErrorCode.CHAT_404_ROOM);
		}

		@Test
		@DisplayName("참여자가 아니면 CHAT_403 이다")
		void header_notParticipant_forbidden() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			given(chatRoomRepository.findDetailById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OTHER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> chatService.header(OTHER_ID, ROOM_ID))
					.isInstanceOf(CustomException.class)
					.extracting(ChatServiceTest::errorCodeOf)
					.isEqualTo(ChatErrorCode.CHAT_403);
		}
	}

	@Nested
	@DisplayName("채팅 메시지 목록")
	class Messages {

		@Test
		@DisplayName("IMAGE 메시지만 images 가 채워지고 TEXT 는 null 이다")
		void messages_textAndImage_fillsImagesOnlyForImageType() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			ChatMessage text = textMessage(1L, room, buyer, "안녕하세요");
			ChatMessage image = imageMessage(2L, room, seller);
			ChatMessageImage img1 = ChatMessageImage.of(image, "k1", 0);
			ChatMessageImage img2 = ChatMessageImage.of(image, "k2", 1);

			given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(chatMessageRepository.findAllByChatRoomId(eq(ROOM_ID), isNull(), any()))
					.willReturn(List.of(image, text));
			given(chatMessageImageRepository.findAllByChatMessageIdIn(List.of(2L)))
					.willReturn(List.of(img1, img2));
			given(fileStorage.toUrl("k1")).willReturn("https://cdn/k1");
			given(fileStorage.toUrl("k2")).willReturn("https://cdn/k2");

			CursorSlice<ChatMessageResponse> slice = chatService.messages(BUYER_ID, ROOM_ID, null, 10);

			assertThat(slice.getContent()).hasSize(2);
			ChatMessageResponse imageResponse = slice.getContent().stream()
					.filter(m -> m.getMessageType() == ChatMessageType.IMAGE).findFirst().orElseThrow();
			assertThat(imageResponse.getContent()).isNull();
			assertThat(imageResponse.getImages()).containsExactly("https://cdn/k1", "https://cdn/k2");

			ChatMessageResponse textResponse = slice.getContent().stream()
					.filter(m -> m.getMessageType() == ChatMessageType.TEXT).findFirst().orElseThrow();
			assertThat(textResponse.getContent()).isEqualTo("안녕하세요");
			assertThat(textResponse.getImages()).isNull();
		}

		@Test
		@DisplayName("size + 1 건을 읽으면 hasNext 가 true 다")
		void messages_moreRowsThanSize_hasNextTrue() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			ChatMessage m1 = textMessage(3L, room, buyer, "c1");
			ChatMessage m2 = textMessage(2L, room, buyer, "c2");
			ChatMessage m3 = textMessage(1L, room, buyer, "c3");

			given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(chatMessageRepository.findAllByChatRoomId(eq(ROOM_ID), isNull(), any()))
					.willReturn(List.of(m1, m2, m3));

			CursorSlice<ChatMessageResponse> slice = chatService.messages(BUYER_ID, ROOM_ID, null, 2);

			assertThat(slice.getContent()).hasSize(2);
			assertThat(slice.getHasNext()).isTrue();
			assertThat(slice.getNextCursor()).isNotNull();
		}
	}

	@Nested
	@DisplayName("메시지 전송")
	class SendMessage {

		@Test
		@DisplayName("TEXT 메시지를 보내면 저장하고 양쪽에 브로드캐스트하며 상대에게 알린다")
		void sendMessage_textValid_savesNotifiesAndBroadcasts() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, SELLER_ID))
					.willReturn(Optional.of(member(room, seller)));
			given(userBlockRepository.existsBlock(anyLong(), anyLong())).willReturn(false);
			given(chatMessageRepository.save(any(ChatMessage.class))).willAnswer(invocation -> {
				ChatMessage saved = invocation.getArgument(0);
				ReflectionTestUtils.setField(saved, "id", 1L);
				ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.now());
				return saved;
			});

			ChatMessageSendRequest request = ChatMessageSendRequest.builder()
					.messageType(ChatMessageType.TEXT).content("안녕하세요").build();

			ChatMessageResponse response = chatService.sendMessage(BUYER_ID, ROOM_ID, request);

			assertThat(response.getContent()).isEqualTo("안녕하세요");
			assertThat(response.getImages()).isNull();
			assertThat(room.getLastMessageAt()).isEqualTo(response.getCreatedAt());

			verify(notificationService).notify(
					eq(seller), eq(NotificationSettingType.CHAT), eq(NotificationCategory.ACTION_REQUIRED),
					eq(item.getName()), any(), eq(item.getCoverImageKey()), eq(NotificationTargetType.CHAT_ROOM),
					eq(ROOM_ID));
			verify(chatWebSocketService).pushMessage(eq(ROOM_ID), eq(BUYER_ID), eq(response));
			verify(chatWebSocketService).pushMessage(eq(ROOM_ID), eq(SELLER_ID), eq(response));
		}

		@Test
		@DisplayName("TEXT 인데 내용이 없으면 CHAT_400_INVALID_MESSAGE 다")
		void sendMessage_textWithoutContent_throwsInvalidMessage() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(userBlockRepository.existsBlock(anyLong(), anyLong())).willReturn(false);

			ChatMessageSendRequest request = ChatMessageSendRequest.builder()
					.messageType(ChatMessageType.TEXT).content("   ").build();

			assertThatThrownBy(() -> chatService.sendMessage(BUYER_ID, ROOM_ID, request))
					.isInstanceOf(CustomException.class)
					.extracting(ChatServiceTest::errorCodeOf)
					.isEqualTo(ChatErrorCode.CHAT_400_INVALID_MESSAGE);

			verify(chatMessageRepository, never()).save(any());
		}

		@Test
		@DisplayName("IMAGE 메시지는 여러 장을 요청 순서대로 저장한다")
		void sendMessage_imageValid_savesImagesInRequestOrder() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(userBlockRepository.existsBlock(anyLong(), anyLong())).willReturn(false);
			given(chatMessageRepository.save(any(ChatMessage.class))).willAnswer(invocation -> {
				ChatMessage saved = invocation.getArgument(0);
				ReflectionTestUtils.setField(saved, "id", 1L);
				ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.now());
				return saved;
			});
			given(fileStorage.toUrl("k1")).willReturn("https://cdn/k1");
			given(fileStorage.toUrl("k2")).willReturn("https://cdn/k2");
			given(fileStorage.toUrl("k3")).willReturn("https://cdn/k3");

			ChatMessageSendRequest request = ChatMessageSendRequest.builder()
					.messageType(ChatMessageType.IMAGE).imageKeys(List.of("k1", "k2", "k3")).build();

			ChatMessageResponse response = chatService.sendMessage(BUYER_ID, ROOM_ID, request);

			assertThat(response.getContent()).isNull();
			assertThat(response.getImages()).containsExactly("https://cdn/k1", "https://cdn/k2", "https://cdn/k3");

			@SuppressWarnings("unchecked")
			ArgumentCaptor<List<ChatMessageImage>> captor = ArgumentCaptor.forClass(List.class);
			verify(chatMessageImageRepository).saveAll(captor.capture());
			assertThat(captor.getValue()).extracting(ChatMessageImage::getImageKey)
					.containsExactly("k1", "k2", "k3");
			assertThat(captor.getValue()).extracting(ChatMessageImage::getSortOrder)
					.containsExactly(0, 1, 2);
		}

		@Test
		@DisplayName("IMAGE 인데 imageKeys 가 없거나 비었으면 CHAT_400_INVALID_MESSAGE 다")
		void sendMessage_imageWithoutKeys_throwsInvalidMessage() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(userBlockRepository.existsBlock(anyLong(), anyLong())).willReturn(false);

			ChatMessageSendRequest nullKeys = ChatMessageSendRequest.builder()
					.messageType(ChatMessageType.IMAGE).imageKeys(null).build();
			ChatMessageSendRequest emptyKeys = ChatMessageSendRequest.builder()
					.messageType(ChatMessageType.IMAGE).imageKeys(List.of()).build();

			assertThatThrownBy(() -> chatService.sendMessage(BUYER_ID, ROOM_ID, nullKeys))
					.isInstanceOf(CustomException.class)
					.extracting(ChatServiceTest::errorCodeOf)
					.isEqualTo(ChatErrorCode.CHAT_400_INVALID_MESSAGE);
			assertThatThrownBy(() -> chatService.sendMessage(BUYER_ID, ROOM_ID, emptyKeys))
					.isInstanceOf(CustomException.class)
					.extracting(ChatServiceTest::errorCodeOf)
					.isEqualTo(ChatErrorCode.CHAT_400_INVALID_MESSAGE);

			verify(chatMessageRepository, never()).save(any());
		}

		@Test
		@DisplayName("메시지 타입이 없으면 CHAT_400_INVALID_MESSAGE 다")
		void sendMessage_nullMessageType_throwsInvalidMessage() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(userBlockRepository.existsBlock(anyLong(), anyLong())).willReturn(false);

			ChatMessageSendRequest request = ChatMessageSendRequest.builder().build();

			assertThatThrownBy(() -> chatService.sendMessage(BUYER_ID, ROOM_ID, request))
					.isInstanceOf(CustomException.class)
					.extracting(ChatServiceTest::errorCodeOf)
					.isEqualTo(ChatErrorCode.CHAT_400_INVALID_MESSAGE);
		}

		@Test
		@DisplayName("차단 관계면 CHAT_403_BLOCKED 다")
		void sendMessage_blockedRelationship_throwsBlocked() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(userBlockRepository.existsBlock(BUYER_ID, SELLER_ID)).willReturn(true);

			ChatMessageSendRequest request = ChatMessageSendRequest.builder()
					.messageType(ChatMessageType.TEXT).content("안녕").build();

			assertThatThrownBy(() -> chatService.sendMessage(BUYER_ID, ROOM_ID, request))
					.isInstanceOf(CustomException.class)
					.extracting(ChatServiceTest::errorCodeOf)
					.isEqualTo(ChatErrorCode.CHAT_403_BLOCKED);

			verify(chatMessageRepository, never()).save(any());
		}

		@Test
		@DisplayName("상대가 나간 뒤에는 알림·push 를 보내지 않는다")
		void sendMessage_counterpartLeft_skipsNotifyAndPush() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);
			ChatRoomMember leftSeller = member(room, seller);
			leftSeller.leave(LocalDateTime.now());

			given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, SELLER_ID))
					.willReturn(Optional.of(leftSeller));
			given(userBlockRepository.existsBlock(anyLong(), anyLong())).willReturn(false);
			given(chatMessageRepository.save(any(ChatMessage.class))).willAnswer(invocation -> {
				ChatMessage saved = invocation.getArgument(0);
				ReflectionTestUtils.setField(saved, "id", 1L);
				ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.now());
				return saved;
			});

			ChatMessageSendRequest request = ChatMessageSendRequest.builder()
					.messageType(ChatMessageType.TEXT).content("상대가 나간 뒤 메시지").build();

			chatService.sendMessage(BUYER_ID, ROOM_ID, request);

			verify(notificationService, never()).notify(
					any(), any(), any(), any(), any(), any(), any(), any());
			verify(chatWebSocketService, never()).pushMessage(eq(ROOM_ID), eq(SELLER_ID), any());
			verify(chatWebSocketService).pushMessage(eq(ROOM_ID), eq(BUYER_ID), any());
		}
	}

	@Nested
	@DisplayName("읽음 처리")
	class Read {

		@Test
		@DisplayName("정상 처리하면 member.markRead 가 호출되고 상대방에게 push 된다")
		void read_valid_marksReadAndPushesToCounterpart() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);
			ChatRoomMember member = member(room, buyer);
			ChatMessage message = textMessage(1L, room, seller, "읽어주세요");

			given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID)).willReturn(Optional.of(member));
			given(chatMessageRepository.findById(1L)).willReturn(Optional.of(message));

			ChatReadRequest request = ChatReadRequest.builder().lastReadMessageId(1L).build();
			chatService.read(BUYER_ID, ROOM_ID, request);

			assertThat(member.getLastReadMessage()).isEqualTo(message);
			verify(chatWebSocketService).pushReadEvent(
					eq(ROOM_ID), eq(SELLER_ID), any(ChatReadEventResponse.class));
		}

		@Test
		@DisplayName("다른 방의 메시지로 읽음 처리를 시도하면 COMMON_400 이다")
		void read_messageFromAnotherRoom_throwsBadRequest() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			ChatRoom otherRoom = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(otherRoom, "id", 999L);
			ChatMessage messageInOtherRoom = textMessage(1L, otherRoom, seller, "다른 방 메시지");

			given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID))
					.willReturn(Optional.of(member(room, buyer)));
			given(chatMessageRepository.findById(1L)).willReturn(Optional.of(messageInOtherRoom));

			ChatReadRequest request = ChatReadRequest.builder().lastReadMessageId(1L).build();

			assertThatThrownBy(() -> chatService.read(BUYER_ID, ROOM_ID, request))
					.isInstanceOf(CustomException.class)
					.extracting(ChatServiceTest::errorCodeOf)
					.isEqualTo(CommonErrorCode.BAD_REQUEST);
		}

		@Test
		@DisplayName("참여자가 아니면 CHAT_403 이다")
		void read_notParticipant_forbidden() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OTHER_ID)).willReturn(Optional.empty());

			ChatReadRequest request = ChatReadRequest.builder().lastReadMessageId(1L).build();

			assertThatThrownBy(() -> chatService.read(OTHER_ID, ROOM_ID, request))
					.isInstanceOf(CustomException.class)
					.extracting(ChatServiceTest::errorCodeOf)
					.isEqualTo(ChatErrorCode.CHAT_403);
		}

		@Test
		@DisplayName("이미 더 최신까지 읽었으면 과거 메시지 id 로 재요청해도 읽음 위치가 뒤로 밀리지 않는다")
		void read_olderMessageAfterNewerAlreadyRead_doesNotRewind() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);
			ChatRoomMember member = member(room, buyer);
			ChatMessage older = textMessage(1L, room, seller, "오래된 메시지");
			ChatMessage newer = textMessage(2L, room, seller, "최신 메시지");
			member.markRead(newer);

			given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID)).willReturn(Optional.of(member));
			given(chatMessageRepository.findById(1L)).willReturn(Optional.of(older));

			ChatReadRequest request = ChatReadRequest.builder().lastReadMessageId(1L).build();
			chatService.read(BUYER_ID, ROOM_ID, request);

			assertThat(member.getLastReadMessage()).isEqualTo(newer);
		}
	}

	@Nested
	@DisplayName("채팅방 나가기")
	class Leave {

		@Test
		@DisplayName("정상적으로 나가면 leftAt 이 채워진다")
		void leave_valid_marksLeftAt() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);
			ChatRoomMember member = member(room, buyer);

			given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, BUYER_ID)).willReturn(Optional.of(member));

			chatService.leave(BUYER_ID, ROOM_ID);

			assertThat(member.getLeftAt()).isNotNull();
		}

		@Test
		@DisplayName("참여자가 아니면 CHAT_403 이다")
		void leave_notParticipant_forbidden() {
			User buyer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, buyer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);

			given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
			given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OTHER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> chatService.leave(OTHER_ID, ROOM_ID))
					.isInstanceOf(CustomException.class)
					.extracting(ChatServiceTest::errorCodeOf)
					.isEqualTo(ChatErrorCode.CHAT_403);
		}
	}

	@Nested
	@DisplayName("채팅방 목록")
	class ListRooms {

		@Test
		@DisplayName("상대는 조회자의 역할에 따라 바뀌고, 메시지가 없는 방은 lastMessage 가 null 이다")
		void list_viewerRoleVaries_counterpartAndLastMessage() {
			User viewer = user(BUYER_ID);
			User counterpartInRoom1 = user(SELLER_ID);
			User counterpartInRoom2 = user(OTHER_ID);

			Item item1 = item(201L, counterpartInRoom1, SaleStatus.ON_SALE, false);
			ItemListing listing1 = listing(1001L, item1, counterpartInRoom1, 100000);
			ChatRoom room1 = ChatRoom.openForListing(item1, listing1, viewer, counterpartInRoom1, LocalDateTime.now());
			ReflectionTestUtils.setField(room1, "id", 101L);

			Item item2 = item(202L, viewer, SaleStatus.ON_SALE, false);
			ItemListing listing2 = listing(1002L, item2, viewer, 200000);
			ChatRoom room2 = ChatRoom.openForListing(item2, listing2, counterpartInRoom2, viewer, LocalDateTime.now());
			ReflectionTestUtils.setField(room2, "id", 102L);

			ChatMessage latestInRoom1 = textMessage(1L, room1, viewer, "안녕하세요");

			given(chatRoomRepository.findAllForFeed(BUYER_ID, ChatRoomTab.ALL, null, null, 20))
					.willReturn(List.of(new ChatRoomFeedRow(room1, 2L), new ChatRoomFeedRow(room2, 0L)));
			given(itemWearRecordRepository.findLastWornDates(any())).willReturn(List.of());
			given(chatMessageRepository.findLatestByChatRoomIdIn(any())).willReturn(List.of(latestInRoom1));

			CursorSlice<ChatRoomSummaryResponse> slice = chatService.list(BUYER_ID, ChatRoomTab.ALL, null, null, 20);

			ChatRoomSummaryResponse row1 = slice.getContent().stream()
					.filter(r -> r.getRoomId().equals(101L)).findFirst().orElseThrow();
			ChatRoomSummaryResponse row2 = slice.getContent().stream()
					.filter(r -> r.getRoomId().equals(102L)).findFirst().orElseThrow();

			assertThat(row1.getCounterpart().getNickname()).isEqualTo(counterpartInRoom1.getNickname());
			assertThat(row1.getLastMessage()).isEqualTo("안녕하세요");
			assertThat(row1.getUnreadCount()).isEqualTo(2);

			assertThat(row2.getCounterpart().getNickname()).isEqualTo(counterpartInRoom2.getNickname());
			assertThat(row2.getLastMessage()).isNull();
		}

		@Test
		@DisplayName("사진 메시지는 '사진을 보냈습니다' 로 미리보기가 바뀐다")
		void list_latestMessageIsImage_previewIsPhotoText() {
			User viewer = user(BUYER_ID);
			User seller = user(SELLER_ID);
			Item item = item(ITEM_ID, seller, SaleStatus.ON_SALE, false);
			ItemListing listing = listing(1000L, item, seller, 360000);
			ChatRoom room = ChatRoom.openForListing(item, listing, viewer, seller, LocalDateTime.now());
			ReflectionTestUtils.setField(room, "id", ROOM_ID);
			ChatMessage latest = imageMessage(1L, room, seller);

			given(chatRoomRepository.findAllForFeed(BUYER_ID, ChatRoomTab.ALL, null, null, 20))
					.willReturn(List.of(new ChatRoomFeedRow(room, 1L)));
			given(itemWearRecordRepository.findLastWornDates(any())).willReturn(List.of());
			given(chatMessageRepository.findLatestByChatRoomIdIn(any())).willReturn(List.of(latest));

			CursorSlice<ChatRoomSummaryResponse> slice = chatService.list(BUYER_ID, ChatRoomTab.ALL, null, null, 20);

			assertThat(slice.getContent().get(0).getLastMessage()).isEqualTo("사진을 보냈습니다");
		}
	}

	// ── 픽스처

	private User user(Long id) {
		User user = User.signUp("user" + id + "@tenure.app", "$2a$10$hashed", "홍길동",
				"821012345678", "닉네임" + id, null, Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private Item item(Long id, User registrant, SaleStatus saleStatus, boolean offerAllowed) {
		Item item = Item.register(registrant, null, "LVC 1955 501", "Levis", "L",
				WearTarget.UNISEX, "item/2026/09/a.jpg", LocalDate.of(2025, 2, 8));
		item.changeSaleStatus(saleStatus);
		item.changeOfferAllowed(offerAllowed);
		ReflectionTestUtils.setField(item, "id", id);
		return item;
	}

	private ItemListing listing(Long id, Item item, User seller, int price) {
		ItemListing listing = ItemListing.open(item, seller, price, 5000, FeeBearer.SELLER, null, LocalDateTime.now());
		ReflectionTestUtils.setField(listing, "id", id);
		return listing;
	}

	private PurchaseOffer offer(Long id, Item item, User buyer, User seller, OfferType type, int price) {
		ShippingAddress address = ShippingAddress.register(buyer, "홍길동", "010-1234-5678", "07027",
				"서울특별시 동작구 상도로 369", "104동 201호", null, null, true);
		PurchaseOffer offer = PurchaseOffer.create(
				null, item, buyer, seller, type,
				price, 5000, FeeBearer.SELLER, new BigDecimal("0.0300"),
				0, 10800, price + 15800, price - 10800, "메모", address, LocalDateTime.now());
		ReflectionTestUtils.setField(offer, "id", id);
		return offer;
	}

	private ChatRoomMember member(ChatRoom room, User user) {
		return ChatRoomMember.join(room, user);
	}

	private ChatMessage textMessage(Long id, ChatRoom room, User sender, String content) {
		ChatMessage message = ChatMessage.ofText(room, sender, content);
		ReflectionTestUtils.setField(message, "id", id);
		ReflectionTestUtils.setField(message, "createdAt", LocalDateTime.now());
		return message;
	}

	private ChatMessage imageMessage(Long id, ChatRoom room, User sender) {
		ChatMessage message = ChatMessage.ofImage(room, sender);
		ReflectionTestUtils.setField(message, "id", id);
		ReflectionTestUtils.setField(message, "createdAt", LocalDateTime.now());
		return message;
	}

	private TradeOrder order(PurchaseOffer offer, Long id) {
		TradeOrder order = TradeOrder.createFromOffer(offer, "2026" + id, LocalDateTime.now());
		ReflectionTestUtils.setField(order, "id", id);
		return order;
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
