package com.tenure.domain.user.entity;

import com.tenure.domain.user.enums.AgreementType;
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
 * 필수 약관 3종 동의 이력.
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "user_agreement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class UserAgreement {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 소유자 FK */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** 약관 3종 */
	@Enumerated(EnumType.STRING)
	@Column(name = "agreement_type", nullable = false, length = 40)
	private AgreementType agreementType;

	/** '모두 동의' 체크박스 */
	@Column(name = "agreed", nullable = false)
	private Boolean agreed;

	/** 동의 시점 기록 */
	@Column(name = "agreed_at", nullable = false)
	private LocalDateTime agreedAt;

	/**
	 * 약관 동의 기록. 법적 증빙이라 append-only 다 — 재동의는 새 행으로 쌓는다.
	 *
	 * <p>{@code agreedAt} 을 여기서 찍는다. 감사 컬럼이 아니라 동의한 시점 자체가 증빙이다.
	 */
	public static UserAgreement agree(User user, AgreementType agreementType) {
		return UserAgreement.builder()
				.user(user)
				.agreementType(agreementType)
				.agreed(true)
				.agreedAt(LocalDateTime.now())
				.build();
	}
}
