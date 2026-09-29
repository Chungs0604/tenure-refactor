package com.tenure.domain.user.entity;

import com.tenure.domain.user.enums.UserGrade;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 등급 변경 이력. UserGrade.RECORD 로의 승격이 언제·왜 일어났는지 기록할 자리.
 *
 * 생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. @Builder 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "user_grade_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class UserGradeHistory {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 대상 사용자 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** 최초 부여 시 NULL */
	@Enumerated(EnumType.STRING)
	@Column(name = "before_grade", length = 20)
	private UserGrade beforeGrade;

	/** 변경 후 등급 */
	@Enumerated(EnumType.STRING)
	@Column(name = "after_grade", nullable = false, length = 20)
	private UserGrade afterGrade;

	/** 평가 구간 시작(재평가 시점 -30일) */
	@Column(name = "window_start")
	private LocalDate windowStart;

	/** 평가 구간 종료 */
	@Column(name = "window_end")
	private LocalDate windowEnd;

	/** 그 구간의 유효 OOTD 수. 승격·강등 판정의 근거값 */
	@Column(name = "valid_ootd_count")
	private Integer validOotdCount;

	/** 변경 시각 */
	@Column(name = "changed_at", nullable = false)
	private LocalDateTime changedAt;


	/**
	 * 등급 변경 기록. 판정 근거를 함께 남긴다 — 나중에 재계산하면 그 사이 삭제된 게시물 때문에
	 * 수치가 달라져 당시 판정을 설명할 수 없다.
	 *
	 * beforeGrade — 최초 부여면 null
	 */
	public static UserGradeHistory of(
			User user, UserGrade beforeGrade, UserGrade afterGrade,
			LocalDate windowStart, LocalDate windowEnd, int validOotdCount
	) {
		return UserGradeHistory.builder()
				.user(user)
				.beforeGrade(beforeGrade)
				.afterGrade(afterGrade)
				.windowStart(windowStart)
				.windowEnd(windowEnd)
				.validOotdCount(validOotdCount)
				.changedAt(LocalDateTime.now())
				.build();
	}

}
