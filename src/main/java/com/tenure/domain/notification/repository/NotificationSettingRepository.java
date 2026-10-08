package com.tenure.domain.notification.repository;

import com.tenure.domain.notification.entity.NotificationSetting;
import com.tenure.domain.notification.enums.NotificationSettingType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

	/**
	 * 발행 전 이 유형을 받기로 했는지 확인함. 가입 시 일괄 생성을 하지 않아서, 행이 없으면
	 * 한 번도 토글을 건드리지 않은 것 — 그 유형의 기본값(NotificationSettingType.isDefaultEnabled)으로 본다
	 */
	Optional<NotificationSetting> findByUserIdAndSettingType(Long userId, NotificationSettingType settingType);

	/** 설정 화면 조회. 토글을 한 번도 안 건드린 유형은 행이 없어 Service 가 기본값으로 채움 */
	List<NotificationSetting> findAllByUserId(Long userId);

	/** 다수에게 동시에 발행할 때(위시리스트 알림 등) 수신자 수만큼 쿼리가 늘지 않게 한 번에 조회함 */
	List<NotificationSetting> findByUserIdInAndSettingType(Collection<Long> userIds, NotificationSettingType settingType);

	/** 회원탈퇴 시 하드 삭제 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from NotificationSetting s where s.user.id = :userId")
	void deleteAllByUserId(@Param("userId") Long userId);
}
