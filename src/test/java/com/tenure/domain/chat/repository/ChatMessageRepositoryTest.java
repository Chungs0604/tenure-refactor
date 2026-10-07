package com.tenure.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.chat.entity.ChatMessage;
import com.tenure.domain.chat.entity.ChatRoom;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.support.RepositorySliceTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * findTopByChatRoomIdOrderByIdDesc·countUnread 검증.
 *
 * 전자는 방 구독 시점의 백로그 읽음 처리, 후자는 메시지 전송 시 목록 실시간 push 의 unreadCount
 * 계산에 쓰임 — 둘 다 presence 기반 실시간 UX 의 핵심 분기라 실제 DB 로 검증한다.
 */
@DisplayName("ChatMessageRepository")
class ChatMessageRepositoryTest extends RepositorySliceTestSupport {

	/** V2 시드의 고정 id — 상의 · 긴소매 티셔츠 */
	private static final Long TOP_SHIRT = 116L;

	@Autowired private EntityManager em;
	@Autowired private ChatMessageRepository chatMessageRepository;

	private User buyer;
	private User seller;
	private ChatRoom room;

	@BeforeEach
	void setUp() {
		buyer = persistUser("buyer@tenure.app", "Buyer");
		seller = persistUser("seller@tenure.app", "Seller");
		room = persistRoom(persistItem(seller), buyer, seller);
	}

	@Nested
	@DisplayName("최신 메시지 조회")
	class FindTopByChatRoomIdOrderByIdDesc {

		@Test
		@DisplayName("메시지가 여러 건이면 id 가 가장 큰(가장 최근) 메시지를 돌려준다")
		void messagesExist_returnsLatestById() {
			persistMessage(room, buyer, "m1");
			persistMessage(room, seller, "m2");
			ChatMessage latest = persistMessage(room, buyer, "m3");

			Optional<ChatMessage> result = chatMessageRepository.findTopByChatRoomIdOrderByIdDesc(room.getId());

			assertThat(result).isPresent();
			assertThat(result.get().getId()).isEqualTo(latest.getId());
		}

		@Test
		@DisplayName("메시지가 없으면 빈 Optional 이다")
		void noMessages_returnsEmpty() {
			Optional<ChatMessage> result = chatMessageRepository.findTopByChatRoomIdOrderByIdDesc(room.getId());

			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("다른 방의 메시지는 조회되지 않는다")
		void onlyLooksAtSpecifiedRoom() {
			ChatRoom otherRoom = persistRoom(persistItem(seller), buyer, seller);
			persistMessage(otherRoom, seller, "다른 방 메시지");

			Optional<ChatMessage> result = chatMessageRepository.findTopByChatRoomIdOrderByIdDesc(room.getId());

			assertThat(result).isEmpty();
		}
	}

	@Nested
	@DisplayName("메시지 커서 페이지네이션")
	class FindAllByChatRoomId {

		@Test
		@DisplayName("커서가 없으면 최신부터 내림차순으로 돌려준다")
		void noCursor_returnsNewestFirst() {
			ChatMessage m1 = persistMessage(room, buyer, "m1");
			ChatMessage m2 = persistMessage(room, seller, "m2");
			ChatMessage m3 = persistMessage(room, buyer, "m3");

			List<ChatMessage> result = chatMessageRepository.findAllByChatRoomId(room.getId(), null, PageRequest.of(0, 10));

			assertThat(result).extracting(ChatMessage::getId)
					.containsExactly(m3.getId(), m2.getId(), m1.getId());
		}

		@Test
		@DisplayName("커서를 주면 그 id 보다 작은 메시지만 돌려준다")
		void withCursor_returnsOnlyOlderMessages() {
			ChatMessage m1 = persistMessage(room, buyer, "m1");
			ChatMessage m2 = persistMessage(room, seller, "m2");
			persistMessage(room, buyer, "m3");

			List<ChatMessage> result = chatMessageRepository
					.findAllByChatRoomId(room.getId(), m2.getId(), PageRequest.of(0, 10));

			assertThat(result).extracting(ChatMessage::getId).containsExactly(m1.getId());
		}

		@Test
		@DisplayName("pageable 크기만큼만 돌려준다")
		void respectsPageableSize() {
			persistMessage(room, buyer, "m1");
			persistMessage(room, seller, "m2");
			persistMessage(room, buyer, "m3");

			List<ChatMessage> result = chatMessageRepository.findAllByChatRoomId(room.getId(), null, PageRequest.of(0, 2));

			assertThat(result).hasSize(2);
		}

		@Test
		@DisplayName("다른 방의 메시지는 섞이지 않는다")
		void excludesMessagesFromOtherRooms() {
			ChatMessage own = persistMessage(room, buyer, "이 방 메시지");
			ChatRoom otherRoom = persistRoom(persistItem(seller), buyer, seller);
			persistMessage(otherRoom, seller, "다른 방 메시지");

			List<ChatMessage> result = chatMessageRepository.findAllByChatRoomId(room.getId(), null, PageRequest.of(0, 10));

			assertThat(result).extracting(ChatMessage::getId).containsExactly(own.getId());
		}
	}

	@Nested
	@DisplayName("안읽음 수 계산")
	class CountUnread {

		@Test
		@DisplayName("lastReadMessageId 가 없으면 상대가 보낸 메시지 전체를 센다")
		void noLastRead_countsAllCounterpartMessages() {
			persistMessage(room, seller, "m1");
			persistMessage(room, seller, "m2");

			long count = chatMessageRepository.countUnread(room.getId(), buyer.getId(), null);

			assertThat(count).isEqualTo(2L);
		}

		@Test
		@DisplayName("lastReadMessageId 이후 메시지만 센다")
		void withLastRead_countsOnlyMessagesAfterIt() {
			ChatMessage m1 = persistMessage(room, seller, "m1");
			persistMessage(room, seller, "m2");
			persistMessage(room, seller, "m3");

			long count = chatMessageRepository.countUnread(room.getId(), buyer.getId(), m1.getId());

			assertThat(count).isEqualTo(2L);
		}

		@Test
		@DisplayName("viewer 본인이 보낸 메시지는 세지 않는다")
		void excludesViewerOwnMessages() {
			persistMessage(room, buyer, "내가 보낸 메시지");
			persistMessage(room, seller, "상대가 보낸 메시지");

			long count = chatMessageRepository.countUnread(room.getId(), buyer.getId(), null);

			assertThat(count).isEqualTo(1L);
		}

		@Test
		@DisplayName("다른 방의 메시지는 세지 않는다")
		void excludesMessagesFromOtherRooms() {
			ChatRoom otherRoom = persistRoom(persistItem(seller), buyer, seller);
			persistMessage(otherRoom, seller, "다른 방 메시지");
			persistMessage(room, seller, "이 방 메시지");

			long count = chatMessageRepository.countUnread(room.getId(), buyer.getId(), null);

			assertThat(count).isEqualTo(1L);
		}

		@Test
		@DisplayName("모두 읽었으면 0 이다")
		void allRead_returnsZero() {
			ChatMessage last = persistMessage(room, seller, "m1");

			long count = chatMessageRepository.countUnread(room.getId(), buyer.getId(), last.getId());

			assertThat(count).isEqualTo(0L);
		}
	}

	// ── 픽스처

	private User persistUser(String email, String nickname) {
		User user = User.signUp(email, "$2a$10$hashed", "홍길동",
				"821012345678", nickname, null, Gender.MALE, 170, 64);
		em.persist(user);
		flush();
		return user;
	}

	private Item persistItem(User owner) {
		Category category = em.find(Category.class, TOP_SHIRT);
		Item item = Item.register(owner, category, "LVC 1955 501", "Levis", "L",
				WearTarget.UNISEX, "item/2026/09/a.jpg", LocalDate.of(2025, 2, 8));
		em.persist(item);
		flush();
		return item;
	}

	private ChatRoom persistRoom(Item item, User roomBuyer, User roomSeller) {
		ChatRoom room = ChatRoom.openForUnsold(item, roomBuyer, roomSeller, LocalDateTime.now());
		em.persist(room);
		flush();
		return room;
	}

	private ChatMessage persistMessage(ChatRoom room, User sender, String content) {
		ChatMessage message = ChatMessage.ofText(room, sender, content);
		em.persist(message);
		flush();
		return message;
	}

	private void flush() {
		em.flush();
	}
}
