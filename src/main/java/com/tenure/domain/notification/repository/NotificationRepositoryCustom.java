package com.tenure.domain.notification.repository;

import com.tenure.domain.notification.entity.Notification;
import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.global.pagination.Cursor;
import java.util.List;

public interface NotificationRepositoryCustom {

	/** 알림 목록. category 로 거르고 커서 페이징함(createdAt desc, id desc). */
	List<Notification> findAllForList(Long userId, NotificationCategory category, Cursor cursor, int size);
}
