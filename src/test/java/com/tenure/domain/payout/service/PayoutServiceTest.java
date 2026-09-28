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
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("PayoutService")
class PayoutServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long OTHER_USER_ID = 2L;
	private static final Long ACCOUNT_ID = 10L;

	@Mock private PayoutAccountRepository payoutAccountRepository;
	@Mock private SettlementRepository settlementRepository;
	@Mock private UserRepository userRepository;

	/** mock 하지 않는다 — mock 하면 엔티티에 필드를 넣고 응답에 안 실어도 테스트가 통과한다. */
	@Spy private PayoutMapper payoutMapper = Mappers.getMapper(PayoutMapper.class);

	@InjectMocks private PayoutService payoutService;

	@Captor private ArgumentCaptor<PayoutAccount> accountCaptor;

	@Nested
	@DisplayName("정산 계좌 목록")
	class GetAccounts {

		@Test
		@DisplayName("계좌를 그대로 내려주고 은행 표시명을 함께 채운다")
		void getAccounts_hasAccounts_returnsAllWithBankName() {
			given(payoutAccountRepository.findAllOf(USER_ID))
					.willReturn(List.of(account(true), account(false)));

			List<PayoutAccountResponse> responses = payoutService.getAccounts(USER_ID);

			// 정렬은 쿼리가 맡는다. 서비스는 순서를 다시 만들지 않는다
			assertThat(responses).hasSize(2);
			assertThat(responses.get(0).getIsDefault()).isTrue();
			assertThat(responses.get(0).getBankCode()).isEqualTo(BankCode.TOSS_BANK);
			assertThat(responses.get(0).getBankName()).isEqualTo("토스뱅크");
		}

		@Test
		@DisplayName("계좌번호를 마스킹하지 않는다 — 수정 화면이 이 값으로 폼을 채운다")
		void getAccounts_hasAccounts_doesNotMaskAccountNumber() {
			given(payoutAccountRepository.findAllOf(USER_ID)).willReturn(List.of(account(true)));

			List<PayoutAccountResponse> responses = payoutService.getAccounts(USER_ID);

			assertThat(responses.get(0).getAccountNumber()).isEqualTo("123456-789012");
		}

		@Test
		@DisplayName("계좌가 없으면 빈 목록이다 — 0건이 정상이다")
		void getAccounts_noAccount_returnsEmpty() {
			given(payoutAccountRepository.findAllOf(USER_ID)).willReturn(List.of());

			assertThat(payoutService.getAccounts(USER_ID)).isEmpty();
		}
	}

	@Nested
	@DisplayName("은행 목록")
	class GetBanks {

		@Test
		@DisplayName("enum 전체를 코드와 표시명으로 내려준다")
		void getBanks_always_returnsAllBanks() {
			List<BankResponse> banks = payoutService.getBanks();

			assertThat(banks).hasSize(BankCode.values().length);
			assertThat(banks).extracting(BankResponse::getCode).contains(BankCode.TOSS_BANK);
			assertThat(banks).extracting(BankResponse::getName).contains("토스뱅크", "KB국민은행");
		}

		@Test
		@DisplayName("상수라 조회하지 않는다")
		void getBanks_always_touchesNoRepository() {
			payoutService.getBanks();

			verifyNoInteractions(payoutAccountRepository, userRepository, settlementRepository);
		}
	}

	@Nested
	@DisplayName("정산 계좌 등록")
	class Create {

		@Test
		@DisplayName("첫 계좌는 기본이 된다")
		void create_firstAccount_becomesDefault() {
			givenUserExists();
			givenSaveAssignsId();
			given(payoutAccountRepository.existsByUserId(USER_ID)).willReturn(false);

			PayoutAccountIdResponse response = payoutService.create(USER_ID, createRequest());

			assertThat(response.getId()).isEqualTo(ACCOUNT_ID);
			verify(payoutAccountRepository).save(accountCaptor.capture());
			assertThat(accountCaptor.getValue().isDefaultAccount()).isTrue();
		}

		@Test
		@DisplayName("두 번째 계좌는 기본이 아니다 — 추가 화면에 기본 설정 UI 가 없다")
		void create_secondAccount_isNotDefault() {
			givenUserExists();
			givenSaveAssignsId();
			given(payoutAccountRepository.existsByUserId(USER_ID)).willReturn(true);

			payoutService.create(USER_ID, createRequest());

			verify(payoutAccountRepository).save(accountCaptor.capture());
			assertThat(accountCaptor.getValue().isDefaultAccount()).isFalse();
			// 기본을 옮기지 않으므로 해제도 없다
			verify(payoutAccountRepository, times(0)).releaseDefault(any());
		}

		@Test
		@DisplayName("보낸 값을 그대로 저장한다")
		void create_validRequest_storesGivenValues() {
			givenUserExists();
			givenSaveAssignsId();
			given(payoutAccountRepository.existsByUserId(USER_ID)).willReturn(false);

			payoutService.create(USER_ID, createRequest());

			verify(payoutAccountRepository).save(accountCaptor.capture());
			PayoutAccount saved = accountCaptor.getValue();
			assertThat(saved.getBankCode()).isEqualTo(BankCode.TOSS_BANK);
			assertThat(saved.getAccountHolder()).isEqualTo("홍길동");
			assertThat(saved.getAccountNumber()).isEqualTo("123456-789012");
		}

		@Test
		@DisplayName("없는 사용자면 USER_404 이고 저장하지 않는다")
		void create_unknownUser_throwsNotFound() {
			given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> payoutService.create(USER_ID, createRequest()))
					.isInstanceOf(CustomException.class)
					.extracting(PayoutServiceTest::errorCodeOf)
					.isEqualTo(UserErrorCode.USER_404);

			verify(payoutAccountRepository, times(0)).save(any());
		}

		private void givenSaveAssignsId() {
			given(payoutAccountRepository.save(any(PayoutAccount.class))).willAnswer(invocation -> {
				PayoutAccount saved = invocation.getArgument(0);
				ReflectionTestUtils.setField(saved, "id", ACCOUNT_ID);
				return saved;
			});
		}
	}

	@Nested
	@DisplayName("정산 계좌 수정")
	class Update {

		@Test
		@DisplayName("보낸 값으로 전체를 덮어쓴다")
		void update_validRequest_overwritesAll() {
			PayoutAccount account = account(false);
			given(payoutAccountRepository.findById(ACCOUNT_ID)).willReturn(Optional.of(account));

			PayoutAccountIdResponse response = payoutService.update(USER_ID, ACCOUNT_ID, updateRequest());

			assertThat(response.getId()).isEqualTo(ACCOUNT_ID);
			assertThat(account.getBankCode()).isEqualTo(BankCode.KOOKMIN);
			assertThat(account.getAccountHolder()).isEqualTo("김유진");
			assertThat(account.getAccountNumber()).isEqualTo("999888-777666");
		}

		@Test
		@DisplayName("기본 계좌 여부는 수정이 건드리지 않는다")
		void update_validRequest_keepsDefaultFlag() {
			PayoutAccount account = account(true);
			given(payoutAccountRepository.findById(ACCOUNT_ID)).willReturn(Optional.of(account));

			payoutService.update(USER_ID, ACCOUNT_ID, updateRequest());

			// 기본 지정은 별도 엔드포인트다. 수정 화면에 기본 설정 UI 가 없다
			assertThat(account.isDefaultAccount()).isTrue();
		}

		@Test
		@DisplayName("없는 계좌면 PAYOUT_404 예외가 발생한다")
		void update_unknownAccount_throwsNotFound() {
			given(payoutAccountRepository.findById(ACCOUNT_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> payoutService.update(USER_ID, ACCOUNT_ID, updateRequest()))
					.isInstanceOf(CustomException.class)
					.extracting(PayoutServiceTest::errorCodeOf)
					.isEqualTo(PayoutErrorCode.PAYOUT_404);
		}

		@Test
		@DisplayName("남의 계좌면 PAYOUT_403 이고 값이 바뀌지 않는다")
		void update_othersAccount_throwsForbidden() {
			PayoutAccount account = account(false);
			given(payoutAccountRepository.findById(ACCOUNT_ID)).willReturn(Optional.of(account));

			assertThatThrownBy(() -> payoutService.update(OTHER_USER_ID, ACCOUNT_ID, updateRequest()))
					.isInstanceOf(CustomException.class)
					.extracting(PayoutServiceTest::errorCodeOf)
					.isEqualTo(PayoutErrorCode.PAYOUT_403);

			assertThat(account.getAccountHolder()).isEqualTo("홍길동");
		}
	}

	@Nested
	@DisplayName("정산 계좌 삭제")
	class Delete {

		@Test
		@DisplayName("기본이 아니고 정산 대기도 없으면 지운다")
		void delete_notDefaultAndNoPendingSettlement_removesRow() {
			PayoutAccount account = account(false);
			given(payoutAccountRepository.findById(ACCOUNT_ID)).willReturn(Optional.of(account));
			given(settlementRepository.existsBySellerIdAndSettledAtIsNull(USER_ID)).willReturn(false);

			payoutService.delete(USER_ID, ACCOUNT_ID);

			// 하드 삭제다. settlement 이 스냅샷을 갖고 있어 확정된 정산은 끊기지 않는다
			verify(payoutAccountRepository).delete(account);
		}

		@Test
		@DisplayName("기본 계좌는 PAYOUT_409_LAST_DEFAULT 로 거부한다")
		void delete_defaultAccount_throwsConflict() {
			given(payoutAccountRepository.findById(ACCOUNT_ID)).willReturn(Optional.of(account(true)));

			assertThatThrownBy(() -> payoutService.delete(USER_ID, ACCOUNT_ID))
					.isInstanceOf(CustomException.class)
					.extracting(PayoutServiceTest::errorCodeOf)
					.isEqualTo(PayoutErrorCode.PAYOUT_409_LAST_DEFAULT);

			verify(payoutAccountRepository, times(0)).delete(any());
			// 기본 계좌면 정산 조회까지 갈 것도 없다
			verifyNoInteractions(settlementRepository);
		}

		@Test
		@DisplayName("입금이 끝나지 않은 정산이 있으면 PAYOUT_409_PENDING_SETTLEMENT 로 거부한다")
		void delete_pendingSettlement_throwsConflict() {
			given(payoutAccountRepository.findById(ACCOUNT_ID)).willReturn(Optional.of(account(false)));
			given(settlementRepository.existsBySellerIdAndSettledAtIsNull(USER_ID)).willReturn(true);

			assertThatThrownBy(() -> payoutService.delete(USER_ID, ACCOUNT_ID))
					.isInstanceOf(CustomException.class)
					.extracting(PayoutServiceTest::errorCodeOf)
					.isEqualTo(PayoutErrorCode.PAYOUT_409_PENDING_SETTLEMENT);

			verify(payoutAccountRepository, times(0)).delete(any());
		}

		@Test
		@DisplayName("없는 계좌면 PAYOUT_404 예외가 발생한다")
		void delete_unknownAccount_throwsNotFound() {
			given(payoutAccountRepository.findById(ACCOUNT_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> payoutService.delete(USER_ID, ACCOUNT_ID))
					.isInstanceOf(CustomException.class)
					.extracting(PayoutServiceTest::errorCodeOf)
					.isEqualTo(PayoutErrorCode.PAYOUT_404);
		}

		@Test
		@DisplayName("남의 계좌면 PAYOUT_403 이고 지우지 않는다")
		void delete_othersAccount_throwsForbidden() {
			given(payoutAccountRepository.findById(ACCOUNT_ID)).willReturn(Optional.of(account(false)));

			assertThatThrownBy(() -> payoutService.delete(OTHER_USER_ID, ACCOUNT_ID))
					.isInstanceOf(CustomException.class)
					.extracting(PayoutServiceTest::errorCodeOf)
					.isEqualTo(PayoutErrorCode.PAYOUT_403);

			verify(payoutAccountRepository, times(0)).delete(any());
		}
	}

	@Nested
	@DisplayName("기본 정산 계좌 지정")
	class ChangeDefault {

		@Test
		@DisplayName("기존 기본을 내리고 지정한 계좌를 올린다")
		void changeDefault_otherAccount_movesDefault() {
			PayoutAccount target = account(false);
			given(payoutAccountRepository.findById(ACCOUNT_ID)).willReturn(Optional.of(target));

			payoutService.changeDefault(USER_ID, ACCOUNT_ID);

			verify(payoutAccountRepository).releaseDefault(USER_ID);
			assertThat(target.isDefaultAccount()).isTrue();
		}

		@Test
		@DisplayName("이미 기본이면 아무것도 하지 않는다")
		void changeDefault_alreadyDefault_doesNothing() {
			PayoutAccount account = account(true);
			given(payoutAccountRepository.findById(ACCOUNT_ID)).willReturn(Optional.of(account));

			payoutService.changeDefault(USER_ID, ACCOUNT_ID);

			// 내렸다 올리면 같은 행을 두 번 건드린다. 해제조차 하지 않는다
			verify(payoutAccountRepository, times(0)).releaseDefault(any());
			assertThat(account.isDefaultAccount()).isTrue();
		}

		@Test
		@DisplayName("남의 계좌면 PAYOUT_403 이다")
		void changeDefault_othersAccount_throwsForbidden() {
			given(payoutAccountRepository.findById(ACCOUNT_ID)).willReturn(Optional.of(account(false)));

			assertThatThrownBy(() -> payoutService.changeDefault(OTHER_USER_ID, ACCOUNT_ID))
					.isInstanceOf(CustomException.class)
					.extracting(PayoutServiceTest::errorCodeOf)
					.isEqualTo(PayoutErrorCode.PAYOUT_403);

			verifyNoInteractions(payoutMapper);
		}
	}

	private void givenUserExists() {
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(owner()));
	}

	/** 계좌의 주인. id 만 쓰이므로 프로필 값은 최소로 채운다. */
	private static User owner() {
		User user = User.signUp("gildong@naver.com", "$2a$10$hashed", "홍길동",
				"821012345678", "Sujun", null, Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", USER_ID);
		return user;
	}

	private static PayoutAccount account(boolean isDefault) {
		PayoutAccount account = PayoutAccount.register(
				owner(), BankCode.TOSS_BANK, "홍길동", "123456-789012", isDefault);
		ReflectionTestUtils.setField(account, "id", ACCOUNT_ID);
		return account;
	}

	private static PayoutAccountCreateRequest createRequest() {
		return PayoutAccountCreateRequest.builder()
				.bankCode(BankCode.TOSS_BANK)
				.accountHolder("홍길동")
				.accountNumber("123456-789012")
				.build();
	}

	private static PayoutAccountUpdateRequest updateRequest() {
		return PayoutAccountUpdateRequest.builder()
				.bankCode(BankCode.KOOKMIN)
				.accountHolder("김유진")
				.accountNumber("999888-777666")
				.build();
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
