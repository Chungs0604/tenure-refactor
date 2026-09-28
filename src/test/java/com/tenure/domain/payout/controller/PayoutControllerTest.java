package com.tenure.domain.payout.controller;

import com.tenure.domain.payout.dto.request.PayoutAccountCreateRequest;
import com.tenure.domain.payout.dto.request.PayoutAccountUpdateRequest;
import com.tenure.domain.payout.dto.response.BankResponse;
import com.tenure.domain.payout.dto.response.PayoutAccountIdResponse;
import com.tenure.domain.payout.dto.response.PayoutAccountResponse;
import com.tenure.domain.payout.enums.BankCode;
import com.tenure.domain.payout.exception.PayoutErrorCode;
import com.tenure.domain.payout.service.PayoutService;
import com.tenure.global.exception.CustomException;
import com.tenure.global.security.AuthUser;
import com.tenure.support.ControllerSliceTestSupport;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PayoutController.class)
@DisplayName("PayoutController")
class PayoutControllerTest extends ControllerSliceTestSupport {

	private static final String ACCOUNTS_URL = "/api/v1/payout-accounts";
	private static final String BANKS_URL = "/api/v1/payout-accounts/banks";
	private static final Long USER_ID = 1L;
	private static final Long ACCOUNT_ID = 10L;

	@MockitoBean private PayoutService payoutService;

	@Nested
	@DisplayName("GET /api/v1/payout-accounts")
	class GetAccounts {

		@Test
		@DisplayName("정산 계좌 목록을 배열로 내려준다")
		void getAccounts_authenticated_returnsArray() throws Exception {
			given(payoutService.getAccounts(USER_ID)).willReturn(List.of(
					response(ACCOUNT_ID, BankCode.TOSS_BANK, true),
					response(11L, BankCode.KOOKMIN, false)));

			mvc.perform(get(ACCOUNTS_URL).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.length()").value(2))
					.andExpect(jsonPath("$.data[0].bankCode").value("TOSS_BANK"))
					.andExpect(jsonPath("$.data[0].bankName").value("토스뱅크"))
					// 수정 화면이 이 값으로 폼을 채운다. 마스킹하면 수정이 깨진다
					.andExpect(jsonPath("$.data[0].accountNumber").value("123456-789012"))
					// 래퍼 타입이라야 키가 isDefault 로 나간다
					.andExpect(jsonPath("$.data[0].isDefault").value(true))
					.andExpect(jsonPath("$.data[1].isDefault").value(false));
		}

		@Test
		@DisplayName("계좌가 없으면 빈 배열이다")
		void getAccounts_noAccount_returnsEmptyArray() throws Exception {
			given(payoutService.getAccounts(USER_ID)).willReturn(List.of());

			mvc.perform(get(ACCOUNTS_URL).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.length()").value(0));
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void getAccounts_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(get(ACCOUNTS_URL))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("COMMON_401"));

			verifyNoInteractions(payoutService);
		}
	}

	@Nested
	@DisplayName("GET /api/v1/payout-accounts/banks")
	class GetBanks {

		@Test
		@DisplayName("은행 목록을 코드와 표시명으로 내려준다")
		void getBanks_authenticated_returnsArray() throws Exception {
			given(payoutService.getBanks())
					.willReturn(List.of(BankResponse.from(BankCode.TOSS_BANK)));

			mvc.perform(get(BANKS_URL).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].code").value("TOSS_BANK"))
					.andExpect(jsonPath("$.data[0].name").value("토스뱅크"));
		}

		@Test
		@DisplayName("계좌 id 경로와 겹치지 않는다")
		void getBanks_authenticated_doesNotHitAccountPath() throws Exception {
			given(payoutService.getBanks()).willReturn(List.of());

			mvc.perform(get(BANKS_URL).with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk());

			verify(payoutService).getBanks();
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void getBanks_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(get(BANKS_URL))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("COMMON_401"));

			verifyNoInteractions(payoutService);
		}
	}

	@Nested
	@DisplayName("POST /api/v1/payout-accounts")
	class Create {

		@Test
		@DisplayName("등록에 성공하면 id 를 내려준다")
		void create_validRequest_returnsId() throws Exception {
			given(payoutService.create(eq(USER_ID), any(PayoutAccountCreateRequest.class)))
					.willReturn(PayoutAccountIdResponse.builder().id(ACCOUNT_ID).build());

			mvc.perform(post(ACCOUNTS_URL)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(createRequest(builder -> {}))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id").value(ACCOUNT_ID));
		}

		@Test
		@DisplayName("은행을 고르지 않으면 400 이고 서비스를 호출하지 않는다")
		void create_nullBankCode_returnsBadRequest() throws Exception {
			expectBadRequest(createRequest(builder -> builder.bankCode(null)));
		}

		@Test
		@DisplayName("목록에 없는 은행이면 400 이다")
		void create_unknownBankCode_returnsBadRequest() throws Exception {
			// 드롭다운에 없는 값을 직접 넣어 보낸 경우다. Jackson 이 enum 을 못 읽어 400 으로 끊긴다
			mvc.perform(post(ACCOUNTS_URL)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"bankCode":"MY_BANK","accountHolder":"홍길동","accountNumber":"123456-789012"}"""))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("COMMON_400"));

			verifyNoInteractions(payoutService);
		}

		@Test
		@DisplayName("예금주가 비면 400 이다")
		void create_blankAccountHolder_returnsBadRequest() throws Exception {
			expectBadRequest(createRequest(builder -> builder.accountHolder(" ")));
		}

		@Test
		@DisplayName("계좌 번호가 비면 400 이다")
		void create_blankAccountNumber_returnsBadRequest() throws Exception {
			expectBadRequest(createRequest(builder -> builder.accountNumber("")));
		}

		@Test
		@DisplayName("계좌 번호가 컬럼 길이를 넘으면 400 이다")
		void create_tooLongAccountNumber_returnsBadRequest() throws Exception {
			expectBadRequest(createRequest(builder -> builder.accountNumber("1".repeat(51))));
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void create_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(post(ACCOUNTS_URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(createRequest(builder -> {}))))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(payoutService);
		}

		private void expectBadRequest(PayoutAccountCreateRequest request) throws Exception {
			mvc.perform(post(ACCOUNTS_URL)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(request)))
					.andExpect(status().isBadRequest());

			verifyNoInteractions(payoutService);
		}
	}

	@Nested
	@DisplayName("PUT /api/v1/payout-accounts/{accountId}")
	class Update {

		@Test
		@DisplayName("수정에 성공하면 id 를 내려준다")
		void update_validRequest_returnsId() throws Exception {
			given(payoutService.update(eq(USER_ID), eq(ACCOUNT_ID), any(PayoutAccountUpdateRequest.class)))
					.willReturn(PayoutAccountIdResponse.builder().id(ACCOUNT_ID).build());

			mvc.perform(put(ACCOUNTS_URL + "/" + ACCOUNT_ID)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(updateRequest())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id").value(ACCOUNT_ID));
		}

		@Test
		@DisplayName("남의 계좌면 403 이다")
		void update_othersAccount_returnsForbidden() throws Exception {
			willThrow(new CustomException(PayoutErrorCode.PAYOUT_403))
					.given(payoutService)
					.update(eq(USER_ID), eq(ACCOUNT_ID), any(PayoutAccountUpdateRequest.class));

			mvc.perform(put(ACCOUNTS_URL + "/" + ACCOUNT_ID)
							.with(authentication(principalOf(USER_ID)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(updateRequest())))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("PAYOUT_403"));
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/payout-accounts/{accountId}")
	class Delete {

		@Test
		@DisplayName("삭제에 성공하면 data 가 null 이다")
		void delete_validRequest_returnsNullData() throws Exception {
			mvc.perform(delete(ACCOUNTS_URL + "/" + ACCOUNT_ID)
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data").doesNotExist());

			verify(payoutService).delete(USER_ID, ACCOUNT_ID);
		}

		@Test
		@DisplayName("기본 계좌면 409 PAYOUT_409_LAST_DEFAULT 다")
		void delete_defaultAccount_returnsConflict() throws Exception {
			willThrow(new CustomException(PayoutErrorCode.PAYOUT_409_LAST_DEFAULT))
					.given(payoutService).delete(USER_ID, ACCOUNT_ID);

			mvc.perform(delete(ACCOUNTS_URL + "/" + ACCOUNT_ID)
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("PAYOUT_409_LAST_DEFAULT"));
		}

		@Test
		@DisplayName("정산 대기 중이면 409 PAYOUT_409_PENDING_SETTLEMENT 다")
		void delete_pendingSettlement_returnsConflict() throws Exception {
			willThrow(new CustomException(PayoutErrorCode.PAYOUT_409_PENDING_SETTLEMENT))
					.given(payoutService).delete(USER_ID, ACCOUNT_ID);

			mvc.perform(delete(ACCOUNTS_URL + "/" + ACCOUNT_ID)
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("PAYOUT_409_PENDING_SETTLEMENT"));
		}

		@Test
		@DisplayName("없는 계좌면 404 다")
		void delete_unknownAccount_returnsNotFound() throws Exception {
			willThrow(new CustomException(PayoutErrorCode.PAYOUT_404))
					.given(payoutService).delete(USER_ID, ACCOUNT_ID);

			mvc.perform(delete(ACCOUNTS_URL + "/" + ACCOUNT_ID)
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("PAYOUT_404"));
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/payout-accounts/{accountId}/default")
	class ChangeDefault {

		@Test
		@DisplayName("지정에 성공하면 data 가 null 이다")
		void changeDefault_validRequest_returnsNullData() throws Exception {
			mvc.perform(patch(ACCOUNTS_URL + "/" + ACCOUNT_ID + "/default")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data").doesNotExist());

			verify(payoutService).changeDefault(USER_ID, ACCOUNT_ID);
		}

		@Test
		@DisplayName("남의 계좌면 403 이다")
		void changeDefault_othersAccount_returnsForbidden() throws Exception {
			willThrow(new CustomException(PayoutErrorCode.PAYOUT_403))
					.given(payoutService).changeDefault(USER_ID, ACCOUNT_ID);

			mvc.perform(patch(ACCOUNTS_URL + "/" + ACCOUNT_ID + "/default")
							.with(authentication(principalOf(USER_ID))))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("PAYOUT_403"));
		}

		@Test
		@DisplayName("토큰이 없으면 401 이다")
		void changeDefault_noAuth_returnsUnauthorized() throws Exception {
			mvc.perform(patch(ACCOUNTS_URL + "/" + ACCOUNT_ID + "/default"))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(payoutService);
		}
	}

	private static UsernamePasswordAuthenticationToken principalOf(Long userId) {
		return new UsernamePasswordAuthenticationToken(new AuthUser(userId), null, List.of());
	}

	private static PayoutAccountResponse response(Long id, BankCode bankCode, boolean isDefault) {
		return PayoutAccountResponse.builder()
				.id(id)
				.bankCode(bankCode)
				.bankName(bankCode.getBankName())
				.accountHolder("홍길동")
				.accountNumber("123456-789012")
				.isDefault(isDefault)
				.build();
	}

	/** 유효한 요청을 만들고, 검사하려는 필드 하나만 덮어쓴다. */
	private static PayoutAccountCreateRequest createRequest(
			Consumer<PayoutAccountCreateRequest.PayoutAccountCreateRequestBuilder> override
	) {
		PayoutAccountCreateRequest.PayoutAccountCreateRequestBuilder builder =
				PayoutAccountCreateRequest.builder()
						.bankCode(BankCode.TOSS_BANK)
						.accountHolder("홍길동")
						.accountNumber("123456-789012");

		override.accept(builder);
		return builder.build();
	}

	private static PayoutAccountUpdateRequest updateRequest() {
		return PayoutAccountUpdateRequest.builder()
				.bankCode(BankCode.KOOKMIN)
				.accountHolder("김유진")
				.accountNumber("999888-777666")
				.build();
	}
}
