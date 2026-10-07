package com.tenure.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.notification.entity.Notification;
import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.domain.notification.enums.NotificationTargetType;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.global.pagination.Cursor;
import com.tenure.support.RepositorySliceTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * findAllForList·markAllRead 검증.
 *
 * 전자는 알림 목록 무한 스크롤의 경계(같은 발행 시각의 tie-break), 후자는 전체 읽음의
 * category 분기와 이미 읽은 행 스킵이 실제 쿼리 동작에 달려 있어 실 DB 로 본다.
 */
@DisplayName("NotificationRepository")
class NotificationRepositoryTest extends RepositorySliceTestSupport {

	@Autowired private EntityManager em;
	@Autowired private NotificationRepository notificationRepository;

	private User user;
	private User other;

	@BeforeEach
	void setUp() {
		user = persistUser("user@tenure.app", "User");
		other = persistUser("other@tenure.app", "Other");
	}

	@Nested
	@DisplayName("알림 목록 커서 페이지네이션")
	class FindAllForList {

		@Test
		@DisplayName("커서가 없으면 최신부터 내림차순으로 돌려준다")
		void noCursor_returnsNewestFirst() {
			Notification n1 = persistNotification(user, NotificationCategory.WISHLIST);
			Notification n2 = persistNotification(user, NotificationCategory.WISHLIST);
			Notification n3 = persistNotification(user, NotificationCategory.WISHLIST);
			backdateCreatedAt(n1, LocalDateTime.now().minusMinutes(3));
			backdateCreatedAt(n2, LocalDateTime.now().minusMinutes(2));
			backdateCreatedAt(n3, LocalDateTime.now().minusMinutes(1));

			List<Notification> result = notificationRepository.findAllForList(user.getId(), null, null, 10);

			assertThat(result).extracting(Notification::getId)
					.containsExactly(n3.getId(), n2.getId(), n1.getId());
		}

		@Test
		@DisplayName("category 를 주면 그 탭만 돌려준다")
		void withCategory_filtersByCategory() {
			Notification wishlist = persistNotification(user, NotificationCategory.WISHLIST);
			persistNotification(user, NotificationCategory.TRADE_STATUS);

			List<Notification> result =
					notificationRepository.findAllForList(user.getId(), NotificationCategory.WISHLIST, null, 10);

			assertThat(result).extracting(Notification::getId).containsExactly(wishlist.getId());
		}

		@Test
		@DisplayName("커서를 주면 그보다 이전 알림만 돌려준다")
		void withCursor_returnsOnlyOlderNotifications() {
			Notification n1 = persistNotification(user, NotificationCategory.WISHLIST);
			Notification n2 = persistNotification(user, NotificationCategory.WISHLIST);
			Notification n3 = persistNotification(user, NotificationCategory.WISHLIST);
			backdateCreatedAt(n1, LocalDateTime.now().minusMinutes(3));
			backdateCreatedAt(n2, LocalDateTime.now().minusMinutes(2));
			backdateCreatedAt(n3, LocalDateTime.now().minusMinutes(1));

			Cursor cursor = Cursor.of(reload(n2).getCreatedAt(), n2.getId());
			List<Notification> result = notificationRepository.findAllForList(user.getId(), null, cursor, 10);

			assertThat(result).extracting(Notification::getId).containsExactly(n1.getId());
		}

		@Test
		@DisplayName("발행 시각이 같아도 id 로 갈라 건너뛰지 않는다")
		void sameCreatedAt_tieBreaksById() {
			Notification first = persistNotification(user, NotificationCategory.WISHLIST);
			Notification second = persistNotification(user, NotificationCategory.WISHLIST);
			LocalDateTime sameMoment = LocalDateTime.now().withNano(0);
			backdateCreatedAt(first, sameMoment);
			backdateCreatedAt(second, sameMoment);

			List<Notification> firstPage = notificationRepository.findAllForList(user.getId(), null, null, 1);
			Notification head = reload(firstPage.get(0));
			Cursor cursor = Cursor.of(head.getCreatedAt(), head.getId());
			List<Notification> next = notificationRepository.findAllForList(user.getId(), null, cursor, 10);

			assertThat(head.getId()).isEqualTo(second.getId());
			assertThat(next).extracting(Notification::getId).containsExactly(first.getId());
		}

		@Test
		@DisplayName("size + 1 건까지 읽는다 — 다음 페이지 유무를 세지 않고 안다")
		void readsOneMoreThanSize() {
			persistNotification(user, NotificationCategory.WISHLIST);
			persistNotification(user, NotificationCategory.WISHLIST);
			persistNotification(user, NotificationCategory.WISHLIST);

			List<Notification> result = notificationRepository.findAllForList(user.getId(), null, null, 2);

			assertThat(result).hasSize(3);
		}

		@Test
		@DisplayName("다른 사용자의 알림은 섞이지 않는다")
		void excludesOtherUsersNotifications() {
			Notification mine = persistNotification(user, NotificationCategory.WISHLIST);
			persistNotification(other, NotificationCategory.WISHLIST);

			List<Notification> result = notificationRepository.findAllForList(user.getId(), null, null, 10);

			assertThat(result).extracting(Notification::getId).containsExactly(mine.getId());
		}
	}

	@Nested
	@DisplayName("전체 읽음 처리")
	class MarkAllRead {

		@Test
		@DisplayName("category 를 지정하면 그 탭만 읽음 처리되고 갱신 건수를 돌려준다")
		void withCategory_updatesOnlyThatCategory() {
			Notification wishlist = persistNotification(user, NotificationCategory.WISHLIST);
			Notification trade = persistNotification(user, NotificationCategory.TRADE_STATUS);

			int updated = notificationRepository.markAllRead(user.getId(), NotificationCategory.WISHLIST, LocalDateTime.now());

			assertThat(updated).isEqualTo(1);
			assertThat(reload(wishlist).getReadAt()).isNotNull();
			assertThat(reload(trade).getReadAt()).isNull();
		}

		@Test
		@DisplayName("category 가 null 이면 모든 탭을 읽음 처리한다")
		void nullCategory_updatesAllCategories() {
			Notification wishlist = persistNotification(user, NotificationCategory.WISHLIST);
			Notification trade = persistNotification(user, NotificationCategory.TRADE_STATUS);

			int updated = notificationRepository.markAllRead(user.getId(), null, LocalDateTime.now());

			assertThat(updated).isEqualTo(2);
			assertThat(reload(wishlist).getReadAt()).isNotNull();
			assertThat(reload(trade).getReadAt()).isNotNull();
		}

		@Test
		@DisplayName("이미 읽은 알림은 갱신 건수에 포함되지 않고 읽은 시각도 바뀌지 않는다")
		void alreadyRead_notCountedAndNotOverwritten() {
			Notification alreadyRead = persistNotification(user, NotificationCategory.WISHLIST);
			LocalDateTime firstReadAt = LocalDateTime.now().minusDays(1).withNano(0);
			alreadyRead.read(firstReadAt);
			em.flush();
			Notification unread = persistNotification(user, NotificationCategory.WISHLIST);

			int updated = notificationRepository.markAllRead(user.getId(), null, LocalDateTime.now());

			assertThat(updated).isEqualTo(1);
			assertThat(reload(alreadyRead).getReadAt()).isEqualTo(firstReadAt);
			assertThat(reload(unread).getReadAt()).isNotNull();
		}

		@Test
		@DisplayName("다른 사용자의 알림은 건드리지 않는다")
		void excludesOtherUsersNotifications() {
			persistNotification(user, NotificationCategory.WISHLIST);
			Notification othersNotification = persistNotification(other, NotificationCategory.WISHLIST);

			int updated = notificationRepository.markAllRead(user.getId(), null, LocalDateTime.now());

			assertThat(updated).isEqualTo(1);
			assertThat(reload(othersNotification).getReadAt()).isNull();
		}

		@Test
		@DisplayName("안읽은 알림 수는 전체 읽음 처리 뒤 0으로 떨어진다")
		void afterMarkAllRead_unreadCountDropsToZero() {
			persistNotification(user, NotificationCategory.WISHLIST);
			persistNotification(user, NotificationCategory.TRADE_STATUS);
			assertThat(notificationRepository.countByUserIdAndReadAtIsNull(user.getId())).isEqualTo(2);

			notificationRepository.markAllRead(user.getId(), null, LocalDateTime.now());

			assertThat(notificationRepository.countByUserIdAndReadAtIsNull(user.getId())).isZero();
		}
	}

	// ── 픽스처

	private User persistUser(String email, String nickname) {
		User user = User.signUp(email, "$2a$10$hashed", "홍길동",
				"821012345678", nickname, null, Gender.MALE, 170, 64);
		em.persist(user);
		em.flush();
		return user;
	}

	private Notification persistNotification(User user, NotificationCategory category) {
		Notification notification = Notification.publish(
				user, category, "제목", "본문", null, NotificationTargetType.ORDER, 1L);
		em.persist(notification);
		em.flush();
		return notification;
	}

	/** 발행 시각은 감사 컬럼이라 엔티티로는 과거로 돌릴 수 없다. */
	private void backdateCreatedAt(Notification notification, LocalDateTime createdAt) {
		em.createNativeQuery("update notification set created_at = ? where id = ?")
				.setParameter(1, createdAt)
				.setParameter(2, notification.getId())
				.executeUpdate();
		em.clear();
	}

	private Notification reload(Notification notification) {
		em.clear();
		return em.find(Notification.class, notification.getId());
	}
}
