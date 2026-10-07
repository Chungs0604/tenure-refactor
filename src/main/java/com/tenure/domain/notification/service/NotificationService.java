package com.tenure.domain.notification.service;

import com.tenure.domain.item.entity.Item;
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
import com.tenure.domain.notification.repository.NotificationRepository;
import com.tenure.domain.notification.repository.NotificationSettingRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.storage.FileStorage;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 발행·조회·읽음 처리, 수신 설정.
 *
 * 예외를 던지기 전에 판단 근거를 남긴다 — ErrorCode 자체는 ServiceLoggingAspect 가 찍는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final NotificationSettingRepository notificationSettingRepository;
	private final UserRepository userRepository;
	private final ItemRepository itemRepository;
	private final NotificationMapper notificationMapper;
	private final FileStorage fileStorage;

	/** 설정에서 꺼둔 유형이면 쌓지 않음. 설정 행이 없으면 한 번도 안 건드린 것 — 그 유형의 기본값으로 봄 */
	@Transactional
	@Loggable("알림 발행")
	public void notify(
			User user, NotificationSettingType settingType, NotificationCategory category,
			String title, String body, String imageKey, NotificationTargetType targetType, Long targetId
	) {
		// 이 유형을 설정 행에서 찾음. 행이 있으면 그 값을 그대로 씀
		// 행이 없으면(가입 이후 이 유형을 한 번도 안 건드린 경우) 쌓을지 말지를 유형별 기본값으로 정함
		boolean enabled = notificationSettingRepository
				.findByUserIdAndSettingType(user.getId(), settingType)
				.map(NotificationSetting::getEnabled)
				.orElse(settingType.isDefaultEnabled());

		// 꺼둔 유형이면 알림 행 자체를 안 만들고 조용히 끝냄 — 에러도 아니고 알릴 것도 없음
		if (!enabled) {
			return;
		}

		// 알림 대상(게시물·아이템·거래 등)이 다형이라 FK 를 걸 수 없음
		// targetType·targetId 두 컬럼만으로 어떤 테이블의 몇 번 행인지 가리킴
		notificationRepository.save(
				Notification.publish(user, category, title, body, imageKey, targetType, targetId));
	}

	/**
	 * 같은 사건을 여러 명에게 동시에 발행할 때(위시리스트 알림 등) 쓴다. 수신자마다 notify 를
	 * 한 번씩 부르면 설정 조회가 수신자 수만큼 나가는데, 여기서는 설정을 한 번에 가져와 거른 뒤
	 * 알림 행도 한 번에 쌓는다
	 */
	@Transactional
	@Loggable("알림 일괄 발행")
	public void notifyAll(
			List<User> users, NotificationSettingType settingType, NotificationCategory category,
			String title, String body, String imageKey, NotificationTargetType targetType, Long targetId
	) {
		// 받을 사람이 없으면 쿼리를 아예 안 보냄 — 빈 목록으로 조회·저장을 부를 이유가 없음
		if (users.isEmpty()) {
			return;
		}

		// 이 유형에 대한 수신자 전체의 설정 행을 한 번에 가져와 userId → enabled 맵으로 만듦
		// 행이 없는 사람은 맵에 안 잡히므로, 꺼낼 때 그 유형의 기본값으로 메움
		List<Long> userIds = users.stream().map(User::getId).toList();
		Map<Long, Boolean> enabledByUserId = notificationSettingRepository
				.findByUserIdInAndSettingType(userIds, settingType).stream()
				.collect(Collectors.toMap(s -> s.getUser().getId(), NotificationSetting::getEnabled));

		// 꺼둔 사람을 걸러내고, 남은사람마다 같은 내용의 알림 엔티티를 만듦
		List<Notification> notifications = users.stream()
				.filter(user -> enabledByUserId.getOrDefault(user.getId(), settingType.isDefaultEnabled()))
				.map(user -> Notification.publish(user, category, title, body, imageKey, targetType, targetId))
				.toList();

		// 걸러진 사람이 다시 0명이면 saveAll 도 빈 목록이라 쿼리가 안 나감
		notificationRepository.saveAll(notifications);
	}

	@Loggable("알림 목록 조회")
	public CursorSlice<NotificationResponse> list(
			Long userId, NotificationCategory category, Cursor cursor, int size
	) {
		// 커서 기준으로 카테고리에 해당하는 한 페이지를 읽어옴. 정렬·필터는 Repository 쪽 쿼리가 담당함
		List<Notification> rows = notificationRepository.findAllForList(userId, category, cursor, size);

		// ITEM 대상인 행만 모아 아이템 삭제 여부를 한 번에 조회함 — 행마다 조회하면 페이지 크기만큼 쿼리가 늘어남
		Map<Long, Boolean> deletedByItemId = resolveDeletedByItemId(rows);

		// 커서 슬라이스로 다음 페이지 유무·다음 커서를 계산한 뒤, 엔티티를 응답 DTO 로 바꿈
		// imageUrl 변환과 targetDeleted 판정은 매퍼가 못 하는 일이라 여기서 미리 만들어 넘김
		return CursorSlice.of(rows, size, n -> Cursor.of(n.getCreatedAt(), n.getId()))
				.map(n -> notificationMapper.toResponse(
						n, resolveImageUrl(n.getImageKey()), isTargetDeleted(n, deletedByItemId))
				);
	}

	/** 알림 읽음. 이미 읽은 알림을 다시 눌러도 에러 없이 멱등하게 끝남 */
	@Transactional
	@Loggable("알림 읽음 처리")
	public void read(Long userId, Long notificationId) {
		// id 로 못 찾으면 404
		Notification notification = notificationRepository.findById(notificationId)
				.orElseThrow(() -> {
					log.info("없는 알림입니다. notificationId={}", notificationId);
					return new CustomException(NotificationErrorCode.NOTIFICATION_404);
				});

		// 찾았더라도 받는 사람이 본인이 아니면 403 — 다른 사람의 알림 행을 읽음 처리할 수 없음
		if (!notification.getUser().getId().equals(userId)) {
			log.info("남의 알림을 읽음 처리하려 했습니다. notificationId={} userId={}", notificationId, userId);
			throw new CustomException(NotificationErrorCode.NOTIFICATION_403);
		}

		// readAt 이 이미 채워져 있으면 엔티티 내부에서 덮어쓰지 않음 — 최초로 읽은 시각을 유지함
		notification.read(LocalDateTime.now());
	}

	/** 전체 읽음. category 를 주면 그 탭만 처리함 */
	@Transactional
	@Loggable("알림 전체 읽음 처리")
	public NotificationReadAllResponse readAll(Long userId, NotificationCategory category) {
		// 벌크 업데이트라 영속성 컨텍스트를 거치지 않음 — 변경된 행 수가 그대로 응답에 쓰임
		int updatedCount = notificationRepository.markAllRead(userId, category, LocalDateTime.now());

		return NotificationReadAllResponse.builder().
				updatedCount(updatedCount).
				build();
	}

	@Loggable("안읽은 알림 수 조회")
	public UnreadCountResponse getUnreadCount(Long userId) {
		int unreadCount = notificationRepository.countByUserIdAndReadAtIsNull(userId);

		return UnreadCountResponse.builder()
				.unreadCount(unreadCount)
				.build();
	}

	/** 설정 화면. 토글을 한 번도 안 건드린 유형은 행이 없어 기본값으로 채움 */
	@Loggable("알림 설정 조회")
	public List<NotificationSettingResponse> getSettings(Long userId) {
		// 이 사용자가 실제로 건드린 설정만 행으로 존재함 — 유형 → enabled 맵으로 바꿔 둠
		Map<NotificationSettingType, Boolean> existing = notificationSettingRepository.findAllByUserId(userId).stream()
				.collect(Collectors.toMap(NotificationSetting::getSettingType, NotificationSetting::getEnabled));

		// 화면에는 9종을 전부 보여줘야 하므로 enum 전체를 돌면서, 행이 없는 유형은 기본값을 채워 넣음
		return Arrays.stream(NotificationSettingType.values())
				.map(type -> notificationMapper.toSettingResponse(
						type, existing.getOrDefault(type, type.isDefaultEnabled())))
				.toList();
	}

	/** 설정 변경. 필수 그룹은 끌 수 없음 — 모두 검증한 뒤에 적용해 일부만 바뀌는 일이 없게 함 */
	@Transactional
	@Loggable("알림 설정 변경")
	public void updateSettings(Long userId, NotificationSettingUpdateRequest request) {
		// 1단계 — 적용 전에 요청 전체를 먼저 검증함. 중간 항목에서 막히면 이미 바뀐 앞쪽 항목을
		// 롤백해야 하는데, 검증을 먼저 끝내 두면 그런 일이 생기지 않음
		request.getSettings().forEach(item -> {
			if (item.getSettingType().getGroup() == NotificationSettingType.Group.REQUIRED
					&& !item.getEnabled()) {
				log.info("필수 알림을 끄려 했습니다. userId={} settingType={}", userId, item.getSettingType());
				throw new CustomException(NotificationErrorCode.NOTIFICATION_400_REQUIRED);
			}
		});

		// 검증을 통과한 뒤에야 연관 엔티티를 조회함 — 행이 없던 유형을 새로 만들 때 필요함
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new CustomException(UserErrorCode.USER_404));

		LocalDateTime now = LocalDateTime.now();

		// 2단계 — 항목마다 기존 행을 찾아 덮어쓰거나, 처음 건드리는 유형이면 그 자리에서 만듦
		request.getSettings().forEach(item -> {
			NotificationSetting setting = notificationSettingRepository
					.findByUserIdAndSettingType(userId, item.getSettingType())
					.orElseGet(() -> notificationSettingRepository.save(
							NotificationSetting.register(user, item.getSettingType(), item.getEnabled(), now)));
			setting.changeEnabled(item.getEnabled(), now);
		});
	}

	/** 이 페이지에 있는 ITEM 대상 알림의 targetId 를 모아 한 번에 조회함(N+1 방지) */
	private Map<Long, Boolean> resolveDeletedByItemId(List<Notification> rows) {
		// ITEM 이 아닌 행(ORDER·OFFER·CHAT_ROOM)은 애초에 삭제 판정 대상이 아니므로 걸러냄
		// 같은 아이템을 가리키는 알림이 여러 건이어도 distinct 로 한 번만 조회함
		List<Long> itemIds = rows.stream()
				.filter(n -> n.getTargetType() == NotificationTargetType.ITEM)
				.map(Notification::getTargetId)
				.distinct()
				.toList();

		// ITEM 대상이 하나도 없으면 조회 자체를 건너뜀 — 빈 IN 절 쿼리를 보낼 이유가 없음
		if (itemIds.isEmpty()) {
			return Map.of();
		}

		// 소프트 삭제된 아이템도 함께 받아야 하므로 findAllByIds 를 씀(활성 아이템만 주는 조회가 아님)
		return itemRepository.findAllByIds(itemIds).stream()
				.collect(Collectors.toMap(Item::getId, Item::isDeleted));
	}

	/** 다형 참조라 조인할 수 없어 타입별로 직접 확인함. ORDER·OFFER·CHAT_ROOM 은 append-only 라 지울 길이 없음 */
	private boolean isTargetDeleted(Notification notification, Map<Long, Boolean> deletedByItemId) {
		// ITEM 이 아니면 애초에 지워질 수 없는 대상이라 항상 false
		if (notification.getTargetType() == NotificationTargetType.ITEM) {
			// 맵에 없으면(이미 하드 삭제되어 조회 자체에 안 걸린 경우) 삭제된 것으로 봄
			return deletedByItemId.getOrDefault(notification.getTargetId(), true);
		}
		return false;
	}

	private String resolveImageUrl(String imageKey) {
		return imageKey == null ? null : fileStorage.toUrl(imageKey);
	}
}
