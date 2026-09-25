package com.tenure.domain.search.entity;

import com.tenure.domain.search.enums.RecentViewTargetType;
import com.tenure.domain.user.entity.User;
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
 * 최근 본 게시물·사용자. 검색 첫 화면의 '최근 본 사용자'와 '방금 보신 OOTD와 유사한 게시물' 추천의 입력이다.
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "recent_view")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class RecentView {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 열람자 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** 게시물 / 사용자 */
	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, length = 20)
	private RecentViewTargetType targetType;

	/** 다형 참조라 FK 없음 */
	@Column(name = "target_id", nullable = false)
	private Long targetId;

	/** '최근' 정렬 기준 */
	@Column(name = "viewed_at", nullable = false)
	private LocalDateTime viewedAt;

}
