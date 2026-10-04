package com.tenure.domain.notification.service;

import com.tenure.domain.notification.entity.Notification;
import com.tenure.domain.notification.entity.NotificationSetting;
import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.domain.notification.enums.NotificationSettingType;
import com.tenure.domain.notification.enums.NotificationTargetType;
import com.tenure.domain.notification.repository.NotificationRepository;
import com.tenure.domain.notification.repository.NotificationSettingRepository;
import com.tenure.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 발행. 목록·읽음 처리 같은 조회 쪽은 아직 없고, 다른 도메인이 사건을 기록하는 쪽만 염
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final NotificationSettingRepository notificationSettingRepository;

	/** 설정에서 꺼둔 유형이면 쌓지 않음. 설정 행이 아직 없으면(드문 경우) 받는 쪽으로 봄 */
	@Transactional
	public void notify(
			User user, NotificationSettingType settingType, NotificationCategory category,
			String title, String body, String imageKey, NotificationTargetType targetType, Long targetId
	) {
		// 유형별 수신 설정 확인 — 꺼둔 유형이면 쌓지 않고 조용히 끝냄
		boolean enabled = notificationSettingRepository
				.findByUserIdAndSettingType(user.getId(), settingType)
				.map(NotificationSetting::getEnabled)
				.orElse(true);

		if (!enabled) {
			return;
		}

		// 대상이 다형이라 FK 는 없음 — targetType·targetId 로만 가리킴
		notificationRepository.save(
				Notification.publish(user, category, title, body, imageKey, targetType, targetId));
	}
}
