package com.tenure.domain.user.entity;

import com.tenure.domain.user.enums.WithdrawalReason;
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
 * 회원탈퇴 사유. UserStatus.WITHDRAWN 의 발생 근거.
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "user_withdrawal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class UserWithdrawal {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 사용자당 1건 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** 'Tenure를 떠나시는 이유를 알려주세요' */
	@Enumerated(EnumType.STRING)
	@Column(name = "reason", nullable = false, length = 40)
	private WithdrawalReason reason;

	/** 탈퇴 시각 */
	@Column(name = "withdrawn_at", nullable = false)
	private LocalDateTime withdrawnAt;

}
