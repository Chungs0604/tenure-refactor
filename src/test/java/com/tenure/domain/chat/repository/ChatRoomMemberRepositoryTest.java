package com.tenure.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.chat.entity.ChatMessage;
import com.tenure.domain.chat.entity.ChatRoom;
import com.tenure.domain.chat.entity.ChatRoomMember;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.support.RepositorySliceTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * markReadIfNewer 검증 — 명시적 읽음 처리·구독 시 백로그 읽음·실시간 수신 중 자동 읽음이
 * 같은 멤버 행을 동시에 건드려도 역행하지 않는다는 보장이 이 메서드 하나에 달려 있어 실제 DB 로 본다.
 */
@DisplayName("ChatRoomMemberRepository")
class ChatRoomMemberRepositoryTest extends RepositorySliceTestSupport {

	/** V2 시드의 고정 id — 상의 · 긴소매 티셔츠 */
	private static final Long TOP_SHIRT = 116L;

	@Autowired private EntityManager em;
	@Autowired private ChatRoomMemberRepository chatRoomMemberRepository;

	private User buyer;
	private User seller;
	private ChatRoom room;
	private ChatRoomMember member;

	@BeforeEach
	void setUp() {
		buyer = persistUser("buyer@tenure.app", "Buyer");
		seller = persistUser("seller@tenure.app", "Seller");
		room = persistRoom(persistItem(seller), buyer, seller);
		member = persistMember(room, buyer);
	}

	@Test
	@DisplayName("아직 읽은 적 없으면(null) 갱신되고 1건을 돌려준다")
	void neverRead_updatesAndReturnsOne() {
		ChatMessage message = persistMessage(room, seller, "m1");

		int affected = chatRoomMemberRepository.markReadIfNewer(member.getId(), message, message.getId());

		assertThat(affected).isEqualTo(1);
		assertThat(reload().getLastReadMessage().getId()).isEqualTo(message.getId());
	}

	@Test
	@DisplayName("현재 저장된 값보다 더 뒤 메시지면 갱신된다")
	void newerThanCurrent_updates() {
		ChatMessage first = persistMessage(room, seller, "m1");
		ChatMessage second = persistMessage(room, seller, "m2");
		chatRoomMemberRepository.markReadIfNewer(member.getId(), first, first.getId());

		int affected = chatRoomMemberRepository.markReadIfNewer(member.getId(), second, second.getId());

		assertThat(affected).isEqualTo(1);
		assertThat(reload().getLastReadMessage().getId()).isEqualTo(second.getId());
	}

	@Test
	@DisplayName("현재 저장된 값보다 더 앞 메시지면 역행하지 않고 0건을 돌려준다")
	void olderThanCurrent_doesNotRegress() {
		ChatMessage first = persistMessage(room, seller, "m1");
		ChatMessage second = persistMessage(room, seller, "m2");
		chatRoomMemberRepository.markReadIfNewer(member.getId(), second, second.getId());

		int affected = chatRoomMemberRepository.markReadIfNewer(member.getId(), first, first.getId());

		assertThat(affected).isEqualTo(0);
		assertThat(reload().getLastReadMessage().getId()).isEqualTo(second.getId());
	}

	@Test
	@DisplayName("이미 같은 메시지까지 읽었으면 다시 갱신하지 않고 0건을 돌려준다")
	void sameAsCurrent_doesNotUpdate() {
		ChatMessage message = persistMessage(room, seller, "m1");
		chatRoomMemberRepository.markReadIfNewer(member.getId(), message, message.getId());

		int affected = chatRoomMemberRepository.markReadIfNewer(member.getId(), message, message.getId());

		assertThat(affected).isEqualTo(0);
	}

	// ── 픽스처

	private User persistUser(String email, String nickname) {
		User user = User.signUp(email, "$2a$10$hashed", "홍길동",
				"821012345678", nickname, null, Gender.MALE, 170, 64);
		em.persist(user);
		em.flush();
		return user;
	}

	private Item persistItem(User owner) {
		Category category = em.find(Category.class, TOP_SHIRT);
		Item item = Item.register(owner, category, "LVC 1955 501", "Levis", "L",
				WearTarget.UNISEX, "item/2026/09/a.jpg", LocalDate.of(2025, 2, 8));
		em.persist(item);
		em.flush();
		return item;
	}

	private ChatRoom persistRoom(Item item, User roomBuyer, User roomSeller) {
		ChatRoom room = ChatRoom.openForUnsold(item, roomBuyer, roomSeller, LocalDateTime.now());
		em.persist(room);
		em.flush();
		return room;
	}

	private ChatRoomMember persistMember(ChatRoom room, User user) {
		ChatRoomMember member = ChatRoomMember.join(room, user);
		em.persist(member);
		em.flush();
		return member;
	}

	private ChatMessage persistMessage(ChatRoom room, User sender, String content) {
		ChatMessage message = ChatMessage.ofText(room, sender, content);
		em.persist(message);
		em.flush();
		return message;
	}

	// 벌크 UPDATE 는 영속성 컨텍스트를 안 거치므로, 1차 캐시 대신 DB 에서 다시 읽어야 실제로 반영됐는지 확인됨
	private ChatRoomMember reload() {
		em.clear();
		return chatRoomMemberRepository.findById(member.getId()).orElseThrow();
	}
}
