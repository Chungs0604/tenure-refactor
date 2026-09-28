package com.tenure.domain.payout.service;

import com.tenure.domain.payout.dto.request.PayoutAccountCreateRequest;
import com.tenure.domain.payout.dto.request.PayoutAccountUpdateRequest;
import com.tenure.domain.payout.dto.response.BankResponse;
import com.tenure.domain.payout.dto.response.PayoutAccountIdResponse;
import com.tenure.domain.payout.dto.response.PayoutAccountResponse;
import com.tenure.domain.payout.entity.PayoutAccount;
import com.tenure.domain.payout.enums.BankCode;
import com.tenure.domain.payout.exception.PayoutErrorCode;
import com.tenure.domain.payout.mapper.PayoutMapper;
import com.tenure.domain.payout.repository.PayoutAccountRepository;
import com.tenure.domain.settlement.repository.SettlementRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 계좌. 판매 전환의 선행 조건이다.
 *
 * 이력을 쌓지 않는다 — 정산은 구매 확정 시점에 계좌 값을 settlement 으로 복사하므로(DEC-013)
 * 계좌를 고치거나 지워도 이미 확정된 정산의 입금처는 그대로다.
 *
 * 기본 계좌는 사용자당 최대 1건이다. 배송지와 달리 0건이 정상이라 첫 계좌를 등록하는 순간
 * 기본으로 올린다. 해제 → 지정 순서는 여기서 지키고, 동시 요청까지는 uk_payout_account_default 가 막는다.
 *
 * 예외를 던지기 전에 판단 근거를 남긴다 — ErrorCode 자체는 ServiceLoggingAspect 가 찍는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayoutService {

	private final PayoutAccountRepository payoutAccountRepository;
	private final SettlementRepository settlementRepository;
	private final UserRepository userRepository;
	private final PayoutMapper payoutMapper;

	/** 정산 계좌 목록. 개수가 적어 페이징하지 않는다. */
	@Loggable("정산 계좌 목록 조회")
	public List<PayoutAccountResponse> getAccounts(Long userId) {

		List<PayoutAccount> accounts = payoutAccountRepository.findAllOf(userId);
		return payoutMapper.toPayoutAccountResponses(accounts);
	}

	/** 은행 드롭다운의 선택지. 상수라 조회가 없다. */
	@Loggable("은행 목록 조회")
	public List<BankResponse> getBanks() {

		return Arrays.stream(BankCode.values())
				.map(BankResponse::from)
				.toList();
	}

	/** 정산 계좌 등록. 첫 계좌는 자동으로 기본이 된다. */
	@Transactional
	@Loggable("정산 계좌 등록")
	public PayoutAccountIdResponse create(Long userId, PayoutAccountCreateRequest request) {
		User user = findUser(userId);

		// 추가 화면에 기본 설정 체크박스가 없다. 고를 여지가 없는 첫 계좌만 기본으로 올린다
		boolean asDefault = !payoutAccountRepository.existsByUserId(userId);

		PayoutAccount account = PayoutAccount.register(
				user,
				request.getBankCode(),
				request.getAccountHolder(),
				request.getAccountNumber(),
				asDefault
		);

		PayoutAccount savedAccount = payoutAccountRepository.save(account);

		return PayoutAccountIdResponse.builder()
				.id(savedAccount.getId())
				.build();
	}

	/** 정산 계좌 수정. 보낸 값으로 전체를 교체한다. */
	@Transactional
	@Loggable("정산 계좌 수정")
	public PayoutAccountIdResponse update(
			Long userId, Long accountId, PayoutAccountUpdateRequest request
	) {
		PayoutAccount account = findOwned(userId, accountId);

		account.update(
				request.getBankCode(),
				request.getAccountHolder(),
				request.getAccountNumber()
		);

		return PayoutAccountIdResponse.builder()
				.id(account.getId())
				.build();
	}

	/**
	 * 정산 계좌 삭제. 하드 삭제다.
	 *
	 * 기본 계좌는 지울 수 없다 — 화면에서도 기본 계좌에만 삭제 버튼이 없다. 승격 대상을 시스템이
	 * 고르면 어느 계좌가 기본이 됐는지 사용자가 알 수 없다.
	 *
	 * 입금이 끝나지 않은 정산이 남아 있어도 막는다. settlement 이 계좌를 스냅샷해 두긴 하지만,
	 * 입금 사고가 났을 때 대조할 원본이 사라지는 것은 다른 문제다.
	 */
	@Transactional
	@Loggable("정산 계좌 삭제")
	public void delete(Long userId, Long accountId) {
		PayoutAccount account = findOwned(userId, accountId);

		if (account.isDefaultAccount()) {
			log.warn("기본 정산 계좌를 삭제하려 했습니다. accountId={}", accountId);
			throw new CustomException(PayoutErrorCode.PAYOUT_409_LAST_DEFAULT);
		}

		if (settlementRepository.existsBySellerIdAndSettledAtIsNull(userId)) {
			log.warn("정산 대기 중인 거래가 있습니다. accountId={}, userId={}", accountId, userId);
			throw new CustomException(PayoutErrorCode.PAYOUT_409_PENDING_SETTLEMENT);
		}

		payoutAccountRepository.delete(account);
	}

	/** 기본 정산 계좌 지정. 기존 기본을 내리고 이 계좌를 올린다. */
	@Transactional
	@Loggable("기본 정산 계좌 지정")
	public void changeDefault(Long userId, Long accountId) {
		PayoutAccount account = findOwned(userId, accountId);

		// 이미 기본이면 내렸다 올리는 셈이라 아무것도 하지 않는다
		if (account.isDefaultAccount()) {
			return;
		}

		payoutAccountRepository.releaseDefault(userId);
		account.changeDefault(true);
	}

	/** 남의 계좌를 건드리지 못하게 막는다. */
	private PayoutAccount findOwned(Long userId, Long accountId) {
		PayoutAccount account = payoutAccountRepository.findById(accountId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 정산 계좌입니다. accountId={}", accountId);
					return new CustomException(PayoutErrorCode.PAYOUT_404);
				});

		// LAZY 프록시의 id 는 DB 를 치지 않고 읽는다
		if (!account.getUser().getId().equals(userId)) {
			log.warn("본인의 정산 계좌가 아닙니다. accountId={}, userId={}", accountId, userId);
			throw new CustomException(PayoutErrorCode.PAYOUT_403);
		}

		return account;
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 사용자입니다. userId={}", userId);
					return new CustomException(UserErrorCode.USER_404);
				});
	}

}
