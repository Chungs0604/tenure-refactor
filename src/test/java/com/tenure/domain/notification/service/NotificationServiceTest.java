package com.tenure.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.notification.dto.request.NotificationSettingUpdateRequest;
import com.tenure.domain.notification.dto.response.NotificationReadAllResponse;
import com.tenure.domain.notification.dto.response.NotificationResponse;
import com.tenure.domain.notification.dto.response.NotificationSettingResponse;
import com.tenure.domain.notification.dto.response.UnreadCountResponse;
import com.tenure.domain.notification.entity.Notification;
import com.tenure.domain.notification.entity.NotificationSetting;
import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.domain.notification.enums.NotificationSettingType;
import com.tenure.domain.notification.enums.NotificationTargetType;
import com.tenure.domain.notification.exception.NotificationErrorCode;
import com.tenure.domain.notification.mapper.NotificationMapper;
import com.tenure.domain.notification.mapper.NotificationMapperImpl;
import com.tenure.domain.notification.repository.NotificationRepository;
import com.tenure.domain.notification.repository.NotificationSettingRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.storage.FileStorage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
@DisplayName("NotificationService")
class NotificationServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long OTHER_USER_ID = 2L;
	private static final Long NOTIFICATION_ID = 100L;
	private static final Long ITEM_ID = 200L;

	@Mock private NotificationRepository notificationRepository;
	@Mock private NotificationSettingRepository notificationSettingRepository;
	@Mock private UserRepository userRepository;
	@Mock private ItemRepository itemRepository;
	@Mock private FileStorage fileStorage;

	// 매핑은 검증 대상이라 mock 이 아니라 실제 생성 구현을 넣는다
	@Spy private NotificationMapper notificationMapper = new NotificationMapperImpl();

	@InjectMocks private NotificationService notificationService;

	@Nested
	@DisplayName("알림 발행")
	class Notify {

		@Test
		@DisplayName("설정 행이 없고 기본값이 ON 인 유형이면 쌓인다")
		void notify_noRowDefaultOn_saves() {
			User user = user(USER_ID);
			given(notificationSettingRepository.findByUserIdAndSettingType(USER_ID, NotificationSettingType.TRADE_REQUEST))
					.willReturn(Optional.empty());

			notificationService.notify(user, NotificationSettingType.TRADE_REQUEST, NotificationCategory.ACTION_REQUIRED,
					"제목", "본문", null, NotificationTargetType.OFFER, 1L);

			verify(notificationRepository).save(any(Notification.class));
		}

		@Test
		@DisplayName("설정 행이 없고 기본값이 OFF 인 유형이면 쌓이지 않는다")
		void notify_noRowDefaultOff_doesNotSave() {
			User user = user(USER_ID);
			given(notificationSettingRepository.findByUserIdAndSettingType(USER_ID, NotificationSettingType.FOLLOW))
					.willReturn(Optional.empty());

			notificationService.notify(user, NotificationSettingType.FOLLOW, NotificationCategory.ACTION_REQUIRED,
					"제목", "본문", null, NotificationTargetType.ORDER, 1L);

			verify(notificationRepository, never()).save(any());
		}

		@Test
		@DisplayName("설정 행이 있으면 기본값 대신 그 값을 따른다")
		void notify_existingRowOverridesDefault_doesNotSave() {
			User user = user(USER_ID);
			NotificationSetting setting = NotificationSetting.register(
					user, NotificationSettingType.TRADE_REQUEST, false, LocalDateTime.now());
			given(notificationSettingRepository.findByUserIdAndSettingType(USER_ID, NotificationSettingType.TRADE_REQUEST))
					.willReturn(Optional.of(setting));

			notificationService.notify(user, NotificationSettingType.TRADE_REQUEST, NotificationCategory.ACTION_REQUIRED,
					"제목", "본문", null, NotificationTargetType.OFFER, 1L);

			verify(notificationRepository, never()).save(any());
		}
	}

	@Nested
	@DisplayName("알림 일괄 발행")
	class NotifyAll {

		@Test
		@DisplayName("설정을 끈 사람은 거르고, 행이 없는 사람은 기본값을 따라 한 번에 쌓는다")
		void notifyAll_filtersByEnabledAndSavesOnce() {
			User enabledByRow = user(1L);
			User disabledByRow = user(2L);
			User noRowDefaultOn = user(3L);

			NotificationSetting disabled = NotificationSetting.register(
					disabledByRow, NotificationSettingType.WISHLIST_ITEM, false, LocalDateTime.now());
			NotificationSetting enabled = NotificationSetting.register(
					enabledByRow, NotificationSettingType.WISHLIST_ITEM, true, LocalDateTime.now());
			given(notificationSettingRepository.findByUserIdInAndSettingType(
					List.of(1L, 2L, 3L), NotificationSettingType.WISHLIST_ITEM))
					.willReturn(List.of(enabled, disabled));

			notificationService.notifyAll(
					List.of(enabledByRow, disabledByRow, noRowDefaultOn), NotificationSettingType.WISHLIST_ITEM,
					NotificationCategory.WISHLIST, "제목", "본문", null, NotificationTargetType.ITEM, 1L);

			ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
			verify(notificationRepository).saveAll(captor.capture());
			assertThat(captor.getValue()).extracting(n -> n.getUser().getId())
					.containsExactlyInAnyOrder(1L, 3L);
		}

		@Test
		@DisplayName("수신자가 없으면 설정 조회도 저장도 하지 않는다")
		void notifyAll_emptyUsers_doesNothing() {
			notificationService.notifyAll(
					List.of(), NotificationSettingType.WISHLIST_ITEM, NotificationCategory.WISHLIST,
					"제목", "본문", null, NotificationTargetType.ITEM, 1L);

			verify(notificationSettingRepository, never()).findByUserIdInAndSettingType(any(), any());
			verify(notificationRepository, never()).saveAll(any());
		}
	}

	@Nested
	@DisplayName("알림 목록 조회")
	class ListNotifications {

		@Test
		@DisplayName("targetType 이 ITEM 이고 아이템이 삭제됐으면 targetDeleted 가 true 다")
		void list_itemTargetDeleted_returnsTargetDeletedTrue() {
			Item item = item(ITEM_ID);
			item.delete(LocalDateTime.now());
			Notification notification = notification(1L, user(USER_ID), NotificationTargetType.ITEM, ITEM_ID, null);

			given(notificationRepository.findAllForList(USER_ID, null, null, 20)).willReturn(List.of(notification));
			given(itemRepository.findAllByIds(List.of(ITEM_ID))).willReturn(List.of(item));

			CursorSlice<NotificationResponse> result = notificationService.list(USER_ID, null, null, 20);

			assertThat(result.getContent().get(0).getTargetDeleted()).isTrue();
		}

		@Test
		@DisplayName("targetType 이 ITEM 이고 아이템이 멀쩡하면 targetDeleted 가 false 다")
		void list_itemTargetAlive_returnsTargetDeletedFalse() {
			Item item = item(ITEM_ID);
			Notification notification = notification(1L, user(USER_ID), NotificationTargetType.ITEM, ITEM_ID, null);

			given(notificationRepository.findAllForList(USER_ID, null, null, 20)).willReturn(List.of(notification));
			given(itemRepository.findAllByIds(List.of(ITEM_ID))).willReturn(List.of(item));

			CursorSlice<NotificationResponse> result = notificationService.list(USER_ID, null, null, 20);

			assertThat(result.getContent().get(0).getTargetDeleted()).isFalse();
		}

		@Test
		@DisplayName("targetType 이 ITEM 이고 아이템이 아예 없으면 targetDeleted 가 true 다")
		void list_itemTargetMissing_returnsTargetDeletedTrue() {
			Notification notification = notification(1L, user(USER_ID), NotificationTargetType.ITEM, ITEM_ID, null);

			given(notificationRepository.findAllForList(USER_ID, null, null, 20)).willReturn(List.of(notification));
			given(itemRepository.findAllByIds(List.of(ITEM_ID))).willReturn(List.of());

			CursorSlice<NotificationResponse> result = notificationService.list(USER_ID, null, null, 20);

			assertThat(result.getContent().get(0).getTargetDeleted()).isTrue();
		}

		@Test
		@DisplayName("targetType 이 ORDER·OFFER·CHAT_ROOM 이면 아이템 레포를 조회하지 않고 항상 false 다")
		void list_nonItemTarget_neverQueriesItemRepositoryAndReturnsFalse() {
			Notification order = notification(1L, user(USER_ID), NotificationTargetType.ORDER, 10L, null);
			Notification offer = notification(2L, user(USER_ID), NotificationTargetType.OFFER, 11L, null);
			Notification chatRoom = notification(3L, user(USER_ID), NotificationTargetType.CHAT_ROOM, 12L, null);

			given(notificationRepository.findAllForList(USER_ID, null, null, 20))
					.willReturn(List.of(order, offer, chatRoom));

			CursorSlice<NotificationResponse> result = notificationService.list(USER_ID, null, null, 20);

			assertThat(result.getContent()).extracting(NotificationResponse::getTargetDeleted)
					.containsExactly(false, false, false);
			verify(itemRepository, never()).findAllByIds(any());
		}

		@Test
		@DisplayName("ITEM 대상 알림이 여러 건이어도 아이템 조회는 한 번만 나간다 — N+1 방지")
		void list_multipleItemTargets_batchesIntoSingleQuery() {
			Item itemA = item(ITEM_ID);
			Item itemB = item(ITEM_ID + 1);
			Notification first = notification(1L, user(USER_ID), NotificationTargetType.ITEM, ITEM_ID, null);
			Notification second = notification(2L, user(USER_ID), NotificationTargetType.ITEM, ITEM_ID + 1, null);

			given(notificationRepository.findAllForList(USER_ID, null, null, 20))
					.willReturn(List.of(first, second));
			given(itemRepository.findAllByIds(List.of(ITEM_ID, ITEM_ID + 1))).willReturn(List.of(itemA, itemB));

			CursorSlice<NotificationResponse> result = notificationService.list(USER_ID, null, null, 20);

			assertThat(result.getContent()).extracting(NotificationResponse::getTargetDeleted)
					.containsExactly(false, false);
			verify(itemRepository, times(1)).findAllByIds(any());
		}

		@Test
		@DisplayName("imageKey 가 null 이면 imageUrl 도 null 이고 저장소를 호출하지 않는다")
		void list_nullImageKey_returnsNullImageUrlWithoutStorageCall() {
			Notification notification = notification(1L, user(USER_ID), NotificationTargetType.ORDER, 10L, null);

			given(notificationRepository.findAllForList(USER_ID, null, null, 20)).willReturn(List.of(notification));

			CursorSlice<NotificationResponse> result = notificationService.list(USER_ID, null, null, 20);

			assertThat(result.getContent().get(0).getImageUrl()).isNull();
			verify(fileStorage, never()).toUrl(any());
		}

		@Test
		@DisplayName("imageKey 가 있으면 저장소로 URL 을 만든다")
		void list_imageKeyPresent_resolvesImageUrl() {
			Notification notification = notification(1L, user(USER_ID), NotificationTargetType.ORDER, 10L, "k1");

			given(notificationRepository.findAllForList(USER_ID, null, null, 20)).willReturn(List.of(notification));
			given(fileStorage.toUrl("k1")).willReturn("https://cdn/k1");

			CursorSlice<NotificationResponse> result = notificationService.list(USER_ID, null, null, 20);

			assertThat(result.getContent().get(0).getImageUrl()).isEqualTo("https://cdn/k1");
		}
	}

	@Nested
	@DisplayName("알림 읽음 처리")
	class Read {

		@Test
		@DisplayName("본인 알림을 읽으면 readAt 이 채워진다")
		void read_ownNotification_setsReadAt() {
			Notification notification = notification(NOTIFICATION_ID, user(USER_ID), NotificationTargetType.ORDER, 1L, null);
			given(notificationRepository.findById(NOTIFICATION_ID)).willReturn(Optional.of(notification));

			notificationService.read(USER_ID, NOTIFICATION_ID);

			assertThat(notification.getReadAt()).isNotNull();
		}

		@Test
		@DisplayName("없는 알림이면 NOTIFICATION_404 다")
		void read_unknownNotification_throwsNotFound() {
			given(notificationRepository.findById(NOTIFICATION_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> notificationService.read(USER_ID, NOTIFICATION_ID))
					.isInstanceOf(CustomException.class)
					.extracting(NotificationServiceTest::errorCodeOf)
					.isEqualTo(NotificationErrorCode.NOTIFICATION_404);
		}

		@Test
		@DisplayName("남의 알림이면 NOTIFICATION_403 이다")
		void read_othersNotification_throwsForbidden() {
			Notification notification = notification(
					NOTIFICATION_ID, user(OTHER_USER_ID), NotificationTargetType.ORDER, 1L, null);
			given(notificationRepository.findById(NOTIFICATION_ID)).willReturn(Optional.of(notification));

			assertThatThrownBy(() -> notificationService.read(USER_ID, NOTIFICATION_ID))
					.isInstanceOf(CustomException.class)
					.extracting(NotificationServiceTest::errorCodeOf)
					.isEqualTo(NotificationErrorCode.NOTIFICATION_403);
		}

		@Test
		@DisplayName("이미 읽은 알림을 다시 읽어도 readAt 이 바뀌지 않는다")
		void read_alreadyRead_doesNotOverwriteReadAt() {
			Notification notification = notification(NOTIFICATION_ID, user(USER_ID), NotificationTargetType.ORDER, 1L, null);
			LocalDateTime firstReadAt = LocalDateTime.of(2026, 1, 1, 0, 0);
			notification.read(firstReadAt);
			given(notificationRepository.findById(NOTIFICATION_ID)).willReturn(Optional.of(notification));

			notificationService.read(USER_ID, NOTIFICATION_ID);

			assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
		}
	}

	@Nested
	@DisplayName("알림 전체 읽음 처리")
	class ReadAll {

		@Test
		@DisplayName("리포지토리가 갱신한 건수를 응답에 그대로 싣는다")
		void readAll_returnsUpdatedCountFromRepository() {
			given(notificationRepository.markAllRead(eq(USER_ID), eq(NotificationCategory.WISHLIST), any()))
					.willReturn(3);

			NotificationReadAllResponse response = notificationService.readAll(USER_ID, NotificationCategory.WISHLIST);

			assertThat(response.getUpdatedCount()).isEqualTo(3);
		}
	}

	@Nested
	@DisplayName("안읽은 알림 수 조회")
	class GetUnreadCount {

		@Test
		@DisplayName("리포지토리 조회 결과를 응답에 그대로 싣는다")
		void getUnreadCount_returnsCountFromRepository() {
			given(notificationRepository.countByUserIdAndReadAtIsNull(USER_ID)).willReturn(5);

			UnreadCountResponse response = notificationService.getUnreadCount(USER_ID);

			assertThat(response.getUnreadCount()).isEqualTo(5);
		}
	}

	@Nested
	@DisplayName("알림 설정 조회")
	class GetSettings {

		@Test
		@DisplayName("행이 하나도 없으면 9종 전부 기본값으로 채워진다")
		void getSettings_noRows_fillsAllWithDefaults() {
			given(notificationSettingRepository.findAllByUserId(USER_ID)).willReturn(List.of());

			List<NotificationSettingResponse> result = notificationService.getSettings(USER_ID);

			assertThat(result).hasSize(NotificationSettingType.values().length);
			assertThat(result)
					.filteredOn(r -> r.getSettingType() == NotificationSettingType.FOLLOW)
					.extracting(NotificationSettingResponse::getEnabled)
					.containsExactly(false);
			assertThat(result)
					.filteredOn(r -> r.getSettingType() == NotificationSettingType.TRADE_REQUEST)
					.extracting(NotificationSettingResponse::getEnabled)
					.containsExactly(true);
		}

		@Test
		@DisplayName("일부 행만 있으면 나머지는 기본값으로 채워진다")
		void getSettings_someRows_fillsRemainingWithDefaults() {
			User user = user(USER_ID);
			NotificationSetting followOn = NotificationSetting.register(
					user, NotificationSettingType.FOLLOW, true, LocalDateTime.now());
			given(notificationSettingRepository.findAllByUserId(USER_ID)).willReturn(List.of(followOn));

			List<NotificationSettingResponse> result = notificationService.getSettings(USER_ID);

			assertThat(result)
					.filteredOn(r -> r.getSettingType() == NotificationSettingType.FOLLOW)
					.extracting(NotificationSettingResponse::getEnabled)
					.containsExactly(true);
			assertThat(result)
					.filteredOn(r -> r.getSettingType() == NotificationSettingType.MARKETING)
					.extracting(NotificationSettingResponse::getEnabled)
					.containsExactly(false);
		}
	}

	@Nested
	@DisplayName("알림 설정 변경")
	class UpdateSettings {

		@Test
		@DisplayName("기존 행이 있으면 덮어쓴다")
		void updateSettings_existingRow_changesEnabled() {
			User user = user(USER_ID);
			NotificationSetting existing = NotificationSetting.register(
					user, NotificationSettingType.FOLLOW, false, LocalDateTime.now());
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
			given(notificationSettingRepository.findByUserIdAndSettingType(USER_ID, NotificationSettingType.FOLLOW))
					.willReturn(Optional.of(existing));

			notificationService.updateSettings(USER_ID, request(NotificationSettingType.FOLLOW, true));

			assertThat(existing.getEnabled()).isTrue();
			verify(notificationSettingRepository, never()).save(any());
		}

		@Test
		@DisplayName("행이 없던 유형을 처음 토글하면 그 자리에서 만든다")
		void updateSettings_noRow_createsNewRow() {
			User user = user(USER_ID);
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
			given(notificationSettingRepository.findByUserIdAndSettingType(USER_ID, NotificationSettingType.FOLLOW))
					.willReturn(Optional.empty());
			given(notificationSettingRepository.save(any(NotificationSetting.class)))
					.willAnswer(invocation -> invocation.getArgument(0));

			notificationService.updateSettings(USER_ID, request(NotificationSettingType.FOLLOW, true));

			verify(notificationSettingRepository).save(any(NotificationSetting.class));
		}

		@Test
		@DisplayName("동시 요청으로 같은 설정 행을 둘 다 새로 만들려 하면 NOTIFICATION_409_SETTING_CONFLICT 로 번역한다")
		void updateSettings_concurrentNoRow_throwsConflict() {
			User user = user(USER_ID);
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
			given(notificationSettingRepository.findByUserIdAndSettingType(USER_ID, NotificationSettingType.FOLLOW))
					.willReturn(Optional.empty());
			willThrow(new DataIntegrityViolationException("uk_notification_setting"))
					.given(notificationSettingRepository).save(any(NotificationSetting.class));

			assertThatThrownBy(() ->
					notificationService.updateSettings(USER_ID, request(NotificationSettingType.FOLLOW, true)))
					.isInstanceOf(CustomException.class)
					.extracting(NotificationServiceTest::errorCodeOf)
					.isEqualTo(NotificationErrorCode.NOTIFICATION_409_SETTING_CONFLICT);
		}

		@Test
		@DisplayName("필수 그룹을 끄려 하면 NOTIFICATION_400_REQUIRED 이고 다른 항목도 반영되지 않는다")
		void updateSettings_requiredGroupOff_throwsAndAppliesNothing() {
			NotificationSettingUpdateRequest request = NotificationSettingUpdateRequest.builder()
					.settings(List.of(
							NotificationSettingUpdateRequest.SettingItem.builder()
									.settingType(NotificationSettingType.FOLLOW).enabled(true).build(),
							NotificationSettingUpdateRequest.SettingItem.builder()
									.settingType(NotificationSettingType.TRADE_REQUEST).enabled(false).build()))
					.build();

			assertThatThrownBy(() -> notificationService.updateSettings(USER_ID, request))
					.isInstanceOf(CustomException.class)
					.extracting(NotificationServiceTest::errorCodeOf)
					.isEqualTo(NotificationErrorCode.NOTIFICATION_400_REQUIRED);

			verify(userRepository, never()).findById(any());
			verify(notificationSettingRepository, never()).findByUserIdAndSettingType(any(), any());
			verify(notificationSettingRepository, never()).save(any());
		}
	}

	// ── 픽스처

	private static NotificationSettingUpdateRequest request(NotificationSettingType type, boolean enabled) {
		return NotificationSettingUpdateRequest.builder()
				.settings(List.of(NotificationSettingUpdateRequest.SettingItem.builder()
						.settingType(type).enabled(enabled).build()))
				.build();
	}

	private static User user(Long id) {
		User user = User.signUp("a@b.com", "$2a$10$hashed", "홍길동", "821012345678",
				"Sujun", null, Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static Item item(Long id) {
		Item item = Item.register(user(OTHER_USER_ID), null, "LVC 1955 501", "Levis", "L",
				WearTarget.UNISEX, "item/2026/09/a.jpg", LocalDate.of(2025, 2, 8));
		ReflectionTestUtils.setField(item, "id", id);
		return item;
	}

	private static Notification notification(
			Long id, User user, NotificationTargetType targetType, Long targetId, String imageKey
	) {
		Notification notification = Notification.publish(
				user, NotificationCategory.ACTION_REQUIRED, "제목", "본문", imageKey, targetType, targetId);
		ReflectionTestUtils.setField(notification, "id", id);
		ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.now());
		return notification;
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
