package com.tenure.domain.payout.entity;

import com.tenure.domain.payout.enums.BankCode;
import com.tenure.domain.user.entity.User;
import com.tenure.global.entity.BaseEntity;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정산 계좌. 판매 전환의 선행 조건이다.
 *
 * <p>정산은 구매 확정 시점에 계좌 값을 settlement 으로 복사하므로(DEC-013), 이 행을 고치거나
 * 지워도 이미 확정된 정산의 입금처는 달라지지 않는다.
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
	@Enumerated(EnumType.STRING)
	@Column(name = "bank_code", nullable = false, length = 20)
	private BankCode bankCode;

	/** '계좌 번호' 1111-1111-1111 */
	@Column(name = "account_number", nullable = false, length = 50)
	private String accountNumber;

	/** '예금주' 김유진 */
	@Column(name = "account_holder", nullable = false, length = 50)
	private String accountHolder;

	/** 목록의 라디오 버튼과 '기본' 배지. 기본 계좌에는 삭제 버튼이 없다 */
	@Column(name = "is_default", nullable = false)
	private Boolean isDefault;

	/** 정산 계좌 등록. 첫 계좌는 고를 여지가 없어 기본이 된다. */
	public static PayoutAccount register(User user, BankCode bankCode,
			String accountHolder, String accountNumber, boolean isDefault
	) {
		return PayoutAccount.builder()
				.user(user)
				.bankCode(bankCode)
				.accountHolder(accountHolder)
				.accountNumber(accountNumber)
				.isDefault(isDefault)
				.build();
	}

	/**
	 * 전체 수정. 이력을 남기지 않고 값을 덮어쓴다.
	 *
	 * <p>기본 계좌 여부는 건드리지 않는다 — 수정 화면에 기본 설정 UI 가 없고 별도 동작이다.
	 */
	public void update(BankCode bankCode, String accountHolder, String accountNumber) {
		this.bankCode = bankCode;
		this.accountHolder = accountHolder;
		this.accountNumber = accountNumber;
	}

	/** 기본 계좌로 올리거나 내린다. 사용자당 1건이라는 단일성은 Service 가 지킨다. */
	public void changeDefault(boolean isDefault) {
		this.isDefault = isDefault;
	}

	public boolean isDefaultAccount() {
		return Boolean.TRUE.equals(isDefault);
	}
}
