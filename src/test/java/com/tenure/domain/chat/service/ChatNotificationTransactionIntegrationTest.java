package com.tenure.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.chat.dto.request.ChatMessageSendRequest;
import com.tenure.domain.chat.entity.ChatRoom;
import com.tenure.domain.chat.entity.ChatRoomMember;
import com.tenure.domain.chat.enums.ChatMessageType;
import com.tenure.domain.chat.repository.ChatMessageRepository;
import com.tenure.domain.chat.repository.ChatRoomMemberRepository;
import com.tenure.domain.chat.repository.ChatRoomRepository;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.notification.entity.Notification;
import com.tenure.domain.notification.enums.NotificationTargetType;
import com.tenure.domain.notification.repository.NotificationRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * 채팅 알림(notify)이 sendMessage() 의 트랜잭션 커밋과 함께 실제로 DB 에 남는지를 본다.
 *
 * <p>ChatServiceTest(Mockito 단위 테스트)는 이 버그를 못 잡는다 — 트랜잭션 매니저가 없어
 * afterCommit 콜백이 등록되는 대신 즉시 실행되고(TransactionSynchronizationManager.
 * isSynchronizationActive() == false 경로), NotificationService 자체도 mock 이라 실제
 * INSERT 가 커밋되는지는 애초에 볼 수 없다. 실제 트랜잭션 매니저·DB 가 있어야 재현·검증된다
 * (OrderBatchIsolationIntegrationTest 와 같은 이유로 통합 테스트가 필요하다).
 */
@DisplayName("채팅 메시지 전송 알림의 트랜잭션 커밋")
class ChatNotificationTransactionIntegrationTest extends IntegrationTestSupport {

	/** V2 시드의 고정 id — 상의 · 긴소매 티셔츠 */
	private static final Long TOP_SHIRT = 116L;

	@Autowired private ChatService chatService;
	@Autowired private ChatRoomRepository chatRoomRepository;
	@Autowired private ChatRoomMemberRepository chatRoomMemberRepository;
	@Autowired private ChatMessageRepository chatMessageRepository;
	@Autowired private ItemRepository itemRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private EntityManager em;

	private User seller;
	private User buyer;
	private Item item;
	private ChatRoom room;

	@BeforeEach
	void setUp() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		seller = userRepository.save(User.signUp(
				"seller-%s@tenure.local".formatted(tag), "$2a$10$hashed", "홍길동",
				"821012345678", "seller-%s".formatted(tag), null, Gender.MALE, 170, 64));
		buyer = userRepository.save(User.signUp(
				"buyer-%s@tenure.local".formatted(tag), "$2a$10$hashed", "김유진",
				"821098765432", "buyer-%s".formatted(tag), null, Gender.FEMALE, 160, 50));

		Category category = em.find(Category.class, TOP_SHIRT);
		item = itemRepository.save(Item.register(seller, category, "LVC 1955 501", "Levis", "L",
				WearTarget.UNISEX, "item/2026/09/a.jpg", LocalDate.of(2025, 2, 8)));

		room = chatRoomRepository.save(ChatRoom.openForUnsold(item, buyer, seller, LocalDateTime.now()));
		chatRoomMemberRepository.save(ChatRoomMember.join(room, buyer));
		chatRoomMemberRepository.save(ChatRoomMember.join(room, seller));
	}

	/*
	 * FK 역순으로 치운다 — notification·chat_message·chat_room_member 가 먼저 사라져야 chat_room·user
	 * 삭제가 막히지 않는다. deleteAllByUserId 는 @Modifying 쿼리라 트랜잭션이 있어야 하는데 이 베이스
	 * (IntegrationTestSupport) 는 테스트에 트랜잭션을 걸지 않으므로, SimpleJpaRepository 가 자체
	 * @Transactional 을 갖는 deleteAll(목록) 로 지운다(OfferConcurrencyTest 와 같은 패턴)
	 */
	@AfterEach
	void tearDown() {
		notificationRepository.deleteAll(notificationRepository.findAll().stream()
				.filter(n -> n.getUser().getId().equals(seller.getId()) || n.getUser().getId().equals(buyer.getId()))
				.toList());
		chatMessageRepository.deleteAll(
				chatMessageRepository.findAllByChatRoomId(room.getId(), null, PageRequest.of(0, 100)));
		chatRoomMemberRepository.findByChatRoomIdAndUserId(room.getId(), buyer.getId())
				.ifPresent(chatRoomMemberRepository::delete);
		chatRoomMemberRepository.findByChatRoomIdAndUserId(room.getId(), seller.getId())
				.ifPresent(chatRoomMemberRepository::delete);
		chatRoomRepository.delete(room);
		itemRepository.delete(item);
		userRepository.delete(buyer);
		userRepository.delete(seller);
	}

	@Test
	@DisplayName("메시지를 보내면 상대에게 보낸 채팅 알림이 커밋되어 다른 트랜잭션에서도 조회된다")
	void sendMessage_notifiesCounterpart_commitsNotification() {
		ChatMessageSendRequest request = ChatMessageSendRequest.builder()
				.messageType(ChatMessageType.TEXT)
				.content("안녕하세요")
				.build();

		// sendMessage() 는 자신의 @Transactional 안에서 끝까지 커밋된 뒤 리턴한다. 그 다음에
		// 새로 보내는 이 조회는 별도 트랜잭션이라, notify 가 진짜 커밋됐는지만 본다 — 같은
		// 영속성 컨텍스트 안의 값을 보는 게 아니다
		chatService.sendMessage(buyer.getId(), room.getId(), request);

		List<Notification> notifications = notificationRepository.findAll().stream()
				.filter(n -> n.getUser().getId().equals(seller.getId()))
				.filter(n -> n.getTargetType() == NotificationTargetType.CHAT_ROOM)
				.filter(n -> n.getTargetId().equals(room.getId()))
				.toList();

		assertThat(notifications)
				.as("notify() 를 afterCommit 밖(= sendMessage 자신의 트랜잭션 안)에서 동기로 불러야 "
						+ "트랜잭션 커밋과 함께 실제로 저장된다. afterCommit 안에서 부르면 새 트랜잭션이 "
						+ "기대한 대로 열리지 않아 INSERT 가 유실된다")
				.hasSize(1);
	}
}
