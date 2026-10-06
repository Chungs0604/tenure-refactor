package com.tenure.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.chat.entity.ChatMessage;
import com.tenure.domain.chat.entity.ChatRoom;
import com.tenure.domain.chat.entity.ChatRoomMember;
import com.tenure.domain.chat.enums.ChatRoomTab;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.global.pagination.Cursor;
import com.tenure.support.RepositorySliceTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 채팅방 목록(API-180) 커스텀 쿼리.
 *
 * H2 가 아니라 실제 MySQL 에서 돈다. tab 필터·키워드 검색·나간 방 제외·안읽음 수 서브쿼리·
 * 커서 페이지네이션이 모두 QueryDSL 한 메서드에 몰려 있어 사람이 실수하기 쉬운 지점이다.
 */
@DisplayName("채팅방 목록 쿼리")
class ChatRoomRepositoryImplTest extends RepositorySliceTestSupport {

	/** V2 시드의 고정 id — 상의 · 긴소매 티셔츠 */
	private static final Long TOP_SHIRT = 116L;

	@Autowired private EntityManager em;
	@Autowired private ChatRoomRepository chatRoomRepository;

	private User buyer;
	private User seller;

	@BeforeEach
	void setUp() {
		buyer = persistUser("buyer@tenure.app", "Buyer");
		seller = persistUser("seller@tenure.app", "Seller");
	}

	@Nested
	@DisplayName("tab 필터")
	class TabFilter {

		@Test
		@DisplayName("BUYING 은 내가 구매자인 방만 담는다")
		void findAllForFeed_buyingTab_onlyRoomsWhereViewerIsBuyer() {
			ChatRoom roomAsBuyer = persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now());
			ChatRoom roomAsSeller = persistRoom(persistItem(buyer), seller, buyer, LocalDateTime.now());
			persistMember(roomAsBuyer, buyer);
			persistMember(roomAsSeller, buyer);

			List<ChatRoomFeedRow> rows =
					chatRoomRepository.findAllForFeed(buyer.getId(), ChatRoomTab.BUYING, null, null, 20);

			assertThat(rows).extracting(r -> r.chatRoom().getId()).containsExactly(roomAsBuyer.getId());
		}

		@Test
		@DisplayName("SELLING 은 내가 판매자인 방만 담는다")
		void findAllForFeed_sellingTab_onlyRoomsWhereViewerIsSeller() {
			ChatRoom roomAsBuyer = persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now());
			ChatRoom roomAsSeller = persistRoom(persistItem(buyer), seller, buyer, LocalDateTime.now());
			persistMember(roomAsBuyer, buyer);
			persistMember(roomAsSeller, buyer);

			List<ChatRoomFeedRow> rows =
					chatRoomRepository.findAllForFeed(buyer.getId(), ChatRoomTab.SELLING, null, null, 20);

			assertThat(rows).extracting(r -> r.chatRoom().getId()).containsExactly(roomAsSeller.getId());
		}

		@Test
		@DisplayName("ALL 은 구매·판매 역할을 가리지 않고 모두 담는다")
		void findAllForFeed_allTab_includesBothRoles() {
			ChatRoom roomAsBuyer = persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now());
			ChatRoom roomAsSeller = persistRoom(persistItem(buyer), seller, buyer, LocalDateTime.now());
			persistMember(roomAsBuyer, buyer);
			persistMember(roomAsSeller, buyer);

			List<ChatRoomFeedRow> rows =
					chatRoomRepository.findAllForFeed(buyer.getId(), ChatRoomTab.ALL, null, null, 20);

			assertThat(rows).extracting(r -> r.chatRoom().getId())
					.containsExactlyInAnyOrder(roomAsBuyer.getId(), roomAsSeller.getId());
		}

		@Test
		@DisplayName("UNREAD 는 안읽음 수가 0보다 큰 방만 담는다")
		void findAllForFeed_unreadTab_onlyUnreadRooms() {
			ChatRoom unreadRoom = persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now());
			persistMember(unreadRoom, buyer);
			persistMessage(unreadRoom, seller, "안읽은 메시지");

			ChatRoom readRoom = persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now());
			ChatRoomMember readMember = persistMember(readRoom, buyer);
			ChatMessage onlyMessage = persistMessage(readRoom, seller, "읽은 메시지");
			readMember.markRead(onlyMessage);
			em.merge(readMember);
			flush();

			List<ChatRoomFeedRow> rows =
					chatRoomRepository.findAllForFeed(buyer.getId(), ChatRoomTab.UNREAD, null, null, 20);

			assertThat(rows).extracting(r -> r.chatRoom().getId()).containsExactly(unreadRoom.getId());
		}
	}

	@Nested
	@DisplayName("키워드 검색")
	class KeywordSearch {

		@Test
		@DisplayName("상대 닉네임으로 찾는다")
		void findAllForFeed_keywordMatchesCounterpartNickname() {
			ChatRoom room = persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now());
			persistMember(room, buyer);

			List<ChatRoomFeedRow> rows =
					chatRoomRepository.findAllForFeed(buyer.getId(), ChatRoomTab.ALL, "Seller", null, 20);

			assertThat(rows).extracting(r -> r.chatRoom().getId()).containsExactly(room.getId());
		}

		@Test
		@DisplayName("아이템명으로 찾는다")
		void findAllForFeed_keywordMatchesItemName() {
			ChatRoom room = persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now());
			persistMember(room, buyer);

			List<ChatRoomFeedRow> rows =
					chatRoomRepository.findAllForFeed(buyer.getId(), ChatRoomTab.ALL, "1955", null, 20);

			assertThat(rows).extracting(r -> r.chatRoom().getId()).containsExactly(room.getId());
		}

		@Test
		@DisplayName("일치하는 상대·아이템명이 없으면 빈 목록이다")
		void findAllForFeed_keywordNoMatch_empty() {
			ChatRoom room = persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now());
			persistMember(room, buyer);

			List<ChatRoomFeedRow> rows =
					chatRoomRepository.findAllForFeed(buyer.getId(), ChatRoomTab.ALL, "존재하지않는검색어", null, 20);

			assertThat(rows).isEmpty();
		}
	}

	@Nested
	@DisplayName("나간 방 제외")
	class LeftRoom {

		@Test
		@DisplayName("내가 나간 방은 목록에서 빠진다")
		void findAllForFeed_myRoomLeft_excludedFromMyList() {
			ChatRoom room = persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now());
			ChatRoomMember myMembership = persistMember(room, buyer);
			myMembership.leave(LocalDateTime.now());
			em.merge(myMembership);
			flush();

			List<ChatRoomFeedRow> rows =
					chatRoomRepository.findAllForFeed(buyer.getId(), ChatRoomTab.ALL, null, null, 20);

			assertThat(rows).isEmpty();
		}

		@Test
		@DisplayName("상대가 나가도 내 목록에는 그대로 남는다")
		void findAllForFeed_counterpartLeft_stillVisibleToMe() {
			ChatRoom room = persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now());
			persistMember(room, buyer);
			ChatRoomMember counterpartMembership = persistMember(room, seller);
			counterpartMembership.leave(LocalDateTime.now());
			em.merge(counterpartMembership);
			flush();

			List<ChatRoomFeedRow> rows =
					chatRoomRepository.findAllForFeed(buyer.getId(), ChatRoomTab.ALL, null, null, 20);

			assertThat(rows).extracting(r -> r.chatRoom().getId()).containsExactly(room.getId());
		}
	}

	@Nested
	@DisplayName("안읽음 수 계산")
	class UnreadCount {

		@Test
		@DisplayName("lastReadMessage 가 없으면 메시지 전체를 안읽음으로 센다")
		void findAllForFeed_noLastRead_countsAllMessages() {
			ChatRoom room = persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now());
			persistMember(room, buyer);
			persistMessage(room, seller, "m1");
			persistMessage(room, seller, "m2");
			persistMessage(room, seller, "m3");

			List<ChatRoomFeedRow> rows =
					chatRoomRepository.findAllForFeed(buyer.getId(), ChatRoomTab.ALL, null, null, 20);

			assertThat(rows).extracting(ChatRoomFeedRow::unreadCount).containsExactly(3L);
		}

		@Test
		@DisplayName("lastReadMessage 이후 메시지만 안읽음으로 센다")
		void findAllForFeed_withLastRead_countsOnlyMessagesAfterIt() {
			ChatRoom room = persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now());
			ChatRoomMember member = persistMember(room, buyer);
			ChatMessage m1 = persistMessage(room, seller, "m1");
			persistMessage(room, seller, "m2");
			persistMessage(room, seller, "m3");
			member.markRead(m1);
			em.merge(member);
			flush();

			List<ChatRoomFeedRow> rows =
					chatRoomRepository.findAllForFeed(buyer.getId(), ChatRoomTab.ALL, null, null, 20);

			assertThat(rows).extracting(ChatRoomFeedRow::unreadCount).containsExactly(2L);
		}

		@Test
		@DisplayName("내가 보낸 메시지는 내 안읽음 수에 들어가지 않는다")
		void findAllForFeed_myOwnMessages_excludedFromMyUnreadCount() {
			ChatRoom room = persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now());
			persistMember(room, buyer);
			persistMessage(room, buyer, "내가 보낸 메시지");
			persistMessage(room, seller, "상대가 보낸 메시지");

			List<ChatRoomFeedRow> rows =
					chatRoomRepository.findAllForFeed(buyer.getId(), ChatRoomTab.ALL, null, null, 20);

			assertThat(rows).extracting(ChatRoomFeedRow::unreadCount).containsExactly(1L);
		}
	}

	@Nested
	@DisplayName("커서 페이지네이션")
	class CursorPaging {

		@Test
		@DisplayName("size + 1 건까지 읽는다")
		void findAllForFeed_readsOneMoreThanSize() {
			persistMember(persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now().minusMinutes(3)), buyer);
			persistMember(persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now().minusMinutes(2)), buyer);
			persistMember(persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now().minusMinutes(1)), buyer);

			List<ChatRoomFeedRow> rows =
					chatRoomRepository.findAllForFeed(buyer.getId(), ChatRoomTab.ALL, null, null, 2);

			assertThat(rows).hasSize(3);
		}

		@Test
		@DisplayName("커서를 주면 그 뒤부터 가져온다")
		void findAllForFeed_withCursor_returnsRoomsAfterIt() {
			ChatRoom older = persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now().minusMinutes(10));
			ChatRoom newer = persistRoom(persistItem(seller), buyer, seller, LocalDateTime.now());
			persistMember(older, buyer);
			persistMember(newer, buyer);

			List<ChatRoomFeedRow> firstPage =
					chatRoomRepository.findAllForFeed(buyer.getId(), ChatRoomTab.ALL, null, null, 1);
			ChatRoom lastOfFirstPage = firstPage.get(0).chatRoom();
			Cursor cursor = Cursor.of(lastOfFirstPage.getLastMessageAt(), lastOfFirstPage.getId());

			List<ChatRoomFeedRow> nextPage =
					chatRoomRepository.findAllForFeed(buyer.getId(), ChatRoomTab.ALL, null, cursor, 20);

			assertThat(lastOfFirstPage.getId()).isEqualTo(newer.getId());
			assertThat(nextPage).extracting(r -> r.chatRoom().getId()).containsExactly(older.getId());
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

	private ChatRoom persistRoom(Item item, User roomBuyer, User roomSeller, LocalDateTime lastMessageAt) {
		ChatRoom room = ChatRoom.openForUnsold(item, roomBuyer, roomSeller, lastMessageAt);
		em.persist(room);
		flush();
		return room;
	}

	private ChatRoomMember persistMember(ChatRoom room, User user) {
		ChatRoomMember member = ChatRoomMember.join(room, user);
		em.persist(member);
		flush();
		return member;
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
