package com.tenure.domain.payout.entity;

import com.tenure.domain.user.entity.User;
import com.tenure.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정산 계좌. 판매 전환의 선행 조건이다.
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "payout_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class PayoutAccount extends BaseEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 소유자 FK */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** '은행' 드롭다운. 예 '토스뱅크' */
	@Column(name = "bank_code", nullable = false, length = 20)
	private String bankCode;

	/** '계좌 번호' 1111-1111-1111 */
	@Column(name = "account_number", nullable = false, length = 50)
	private String accountNumber;

	/** '예금주' 김유진 */
	@Column(name = "account_holder", nullable = false, length = 50)
	private String accountHolder;

	/** 복수 계좌 등록 가능한 목록 화면('정산계좌 관리') */
	@Column(name = "is_default", nullable = false)
	private Boolean isDefault;

}
