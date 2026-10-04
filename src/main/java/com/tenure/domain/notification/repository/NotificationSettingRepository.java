package com.tenure.domain.notification.repository;

import com.tenure.domain.notification.entity.NotificationSetting;
import com.tenure.domain.notification.enums.NotificationSettingType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

	/** 발행 전 이 유형을 받기로 했는지 확인함. 행이 없으면 아직 가입 초기화가 안 된 드문 경우라 받는 쪽으로 봄 */
	Optional<NotificationSetting> findByUserIdAndSettingType(Long userId, NotificationSettingType settingType);
}
