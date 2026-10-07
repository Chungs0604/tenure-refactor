package com.tenure.domain.notification.repository;

import com.tenure.domain.notification.entity.Notification;
import com.tenure.domain.notification.enums.NotificationCategory;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long>, NotificationRepositoryCustom {

	/** 헤더 벨 배지. ix_notification_unread(user_id, read_at) 가 받음 */
	int countByUserIdAndReadAtIsNull(Long userId);

	/** 전체 읽음. category 를 주면 그 탭만 처리함 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Notification n set n.readAt = :now "
			+ "where n.user.id = :userId and n.readAt is null "
			+ "and (:category is null or n.category = :category)")
	int markAllRead(
			@Param("userId") Long userId,
			@Param("category") NotificationCategory category,
			@Param("now") LocalDateTime now
	);
}
