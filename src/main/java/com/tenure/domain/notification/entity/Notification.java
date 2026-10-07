package com.tenure.domain.notification.entity;

import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.domain.notification.enums.NotificationTargetType;
import com.tenure.domain.user.entity.User;
import com.tenure.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림. 화면의 탭 구성이 그대로 category 다.
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Notification extends BaseCreatedEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 수신자 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** 탭 4종 */
	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 30)
	private NotificationCategory category;

	/** 알림 목록 항목 제목 */
	@Column(name = "title", nullable = false, length = 200)
	private String title;

	/** 알림 본문 */
	@Column(name = "body", length = 500)
	private String body;

	/** 행 썸네일. 다형 참조라 대상과 조인할 수 없어 발행 시점에 스냅샷한다 */
	@Column(name = "image_key", length = 500)
	private String imageKey;

	/** 딥링크 대상 종류 */
	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", length = 30)
	private NotificationTargetType targetType;

	/** 다형 참조라 FK 없음 */
	@Column(name = "target_id")
	private Long targetId;

	/** 읽음 표시 (읽지 않은 항목 강조) */
	@Column(name = "read_at")
	private LocalDateTime readAt;

	/** 알림 발행. 대상이 다형이라 FK 는 걸지 않고 targetId 만 받음 */
	public static Notification publish(
			User user, NotificationCategory category, String title, String body,
			String imageKey, NotificationTargetType targetType, Long targetId
	) {
		return Notification.builder()
				.user(user)
				.category(category)
				.title(title)
				.body(body)
				.imageKey(imageKey)
				.targetType(targetType)
				.targetId(targetId)
				.build();
	}

	/** 읽음 처리. 이미 읽었으면 그대로 둠 — 나중에 읽은 시각으로 덮어쓰지 않음 */
	public void read(LocalDateTime now) {
		if (this.readAt == null) {
			this.readAt = now;
		}
	}

}
