package com.tenure.domain.auth.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.tenure.domain.auth.enums.VerificationPurpose;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Redis 키 구성·정규화. 실제 Redis 연결 대신 {@link StringRedisTemplate} 을 모킹한다 —
 * {@code HolidayStoreTest} 와 같은 패턴이다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailVerificationStore")
class EmailVerificationStoreTest {

	private static final VerificationPurpose PURPOSE = VerificationPurpose.SIGN_UP;

	@Mock private StringRedisTemplate redis;
	@Mock private ValueOperations<String, String> valueOperations;

	@InjectMocks private EmailVerificationStore store;

	@Nested
	@DisplayName("이메일 대소문자 정규화")
	class CaseNormalization {

		@Test
		@DisplayName("발급과 조회에 다른 대소문자를 써도 같은 코드 키를 본다")
		void issueAndFindCode_differentCase_hitSameKey() {
			// DB collation(utf8mb4_0900_ai_ci)과 같은 기준으로 대소문자를 무시해야 한다 — 안 그러면
			// 모바일 자동 대문자화 같은 흔한 경로에서 "인증번호가 만료되었습니다"로 잘못 뜬다
			given(redis.opsForValue()).willReturn(valueOperations);

			store.issueCode("E2E.CaseTest.X@Tenure-Test.local", PURPOSE);
			store.findCode("e2e.casetest.x@tenure-test.local", PURPOSE);

			String expectedKey = "email-verify:SIGN_UP:e2e.casetest.x@tenure-test.local";
			verify(valueOperations).set(eq(expectedKey), anyString(), any(Duration.class));
			verify(valueOperations).get(expectedKey);
		}

		@Test
		@DisplayName("재전송 쿨다운도 같은 기준으로 정규화한다")
		void sendCooldown_differentCase_hitSameKey() {
			given(redis.hasKey("email-verify-cooldown:SIGN_UP:gildong@naver.com")).willReturn(true);

			assertThat(store.isInSendCooldown("Gildong@Naver.com", PURPOSE)).isTrue();
		}

		@Test
		@DisplayName("일일 발송 한도도 같은 기준으로 정규화한다")
		void dailySendLimit_differentCase_hitSameKey() {
			given(redis.opsForValue()).willReturn(valueOperations);
			given(valueOperations.get("email-verify-sendcount:SIGN_UP:gildong@naver.com")).willReturn("5");

			assertThat(store.exceedsDailySendLimit("GILDONG@NAVER.COM", PURPOSE)).isTrue();
		}

		@Test
		@DisplayName("대조 실패 횟수도 같은 기준으로 정규화한다")
		void confirmAttempts_differentCase_hitSameKey() {
			given(redis.opsForValue()).willReturn(valueOperations);
			given(valueOperations.increment("email-verify-attempt:SIGN_UP:gildong@naver.com")).willReturn(1L);

			assertThat(store.incrementConfirmAttempts("GILDONG@NAVER.COM", PURPOSE)).isEqualTo(1L);
		}
	}

	@Nested
	@DisplayName("인증번호 대조 시도 횟수")
	class ConfirmAttempts {

		@Test
		@DisplayName("첫 실패면 코드와 같은 TTL 을 건다")
		void incrementConfirmAttempts_firstFailure_setsTtl() {
			given(redis.opsForValue()).willReturn(valueOperations);
			given(valueOperations.increment("email-verify-attempt:SIGN_UP:gildong@naver.com")).willReturn(1L);

			store.incrementConfirmAttempts("gildong@naver.com", PURPOSE);

			// TTL 을 안 걸면 이 카운터가 영원히 남아, 다음 번 발급된 코드의 시도 횟수에 섞여든다
			verify(redis).expire("email-verify-attempt:SIGN_UP:gildong@naver.com", EmailVerificationStore.CODE_TTL);
		}

		@Test
		@DisplayName("두 번째 이후 실패면 TTL 을 다시 걸지 않는다")
		void incrementConfirmAttempts_secondFailure_doesNotResetTtl() {
			given(redis.opsForValue()).willReturn(valueOperations);
			given(valueOperations.increment("email-verify-attempt:SIGN_UP:gildong@naver.com")).willReturn(2L);

			store.incrementConfirmAttempts("gildong@naver.com", PURPOSE);

			// 매번 TTL 을 다시 걸면 계속 대입하는 동안 만료가 미뤄져 창이 넓어진다
			verify(redis, never()).expire(anyString(), any(Duration.class));
		}

		@Test
		@DisplayName("새 인증번호를 발급하면 이전 코드의 대조 실패 횟수를 지운다")
		void issueCode_clearsPreviousAttemptCount() {
			given(redis.opsForValue()).willReturn(valueOperations);

			store.issueCode("gildong@naver.com", PURPOSE);

			// 안 지우면 재발송 직후 몇 번 틀렸을 뿐인데 누적 횟수가 이전 코드 것과 합쳐져
			// 새 코드가 바로 무효화될 수 있다
			verify(redis).delete("email-verify-attempt:SIGN_UP:gildong@naver.com");
		}
	}

	@Nested
	@DisplayName("발송 제한 원자적 예약")
	class SendReservation {

		@Test
		@DisplayName("쿨다운 중이 아니면 SETNX 로 예약하고 true 를 돌려준다")
		void reserveSendCooldown_notInCooldown_reservesAndReturnsTrue() {
			given(redis.opsForValue()).willReturn(valueOperations);
			given(valueOperations.setIfAbsent(
					"email-verify-cooldown:SIGN_UP:gildong@naver.com", "1", EmailVerificationStore.SEND_COOLDOWN))
					.willReturn(true);

			assertThat(store.reserveSendCooldown("gildong@naver.com", PURPOSE)).isTrue();
		}

		@Test
		@DisplayName("이미 쿨다운 중이면(동시 요청 포함) SETNX 가 실패해 false 를 돌려준다")
		void reserveSendCooldown_alreadyReserved_returnsFalse() {
			given(redis.opsForValue()).willReturn(valueOperations);
			given(valueOperations.setIfAbsent(
					"email-verify-cooldown:SIGN_UP:gildong@naver.com", "1", EmailVerificationStore.SEND_COOLDOWN))
					.willReturn(false);

			assertThat(store.reserveSendCooldown("gildong@naver.com", PURPOSE)).isFalse();
		}

		@Test
		@DisplayName("증가 후 값이 일일 한도를 넘지 않으면 true 다")
		void incrementAndCheckDailySend_withinLimit_returnsTrue() {
			given(redis.opsForValue()).willReturn(valueOperations);
			given(valueOperations.increment("email-verify-sendcount:SIGN_UP:gildong@naver.com"))
					.willReturn((long) EmailVerificationStore.MAX_DAILY_SEND);

			assertThat(store.incrementAndCheckDailySend("gildong@naver.com", PURPOSE)).isTrue();
		}

		@Test
		@DisplayName("증가 후 값이 일일 한도를 넘으면 false 다 — 읽고 나서 늘리는 두 단계가 아니라 INCR 결과 자체로 판정한다")
		void incrementAndCheckDailySend_overLimit_returnsFalse() {
			given(redis.opsForValue()).willReturn(valueOperations);
			given(valueOperations.increment("email-verify-sendcount:SIGN_UP:gildong@naver.com"))
					.willReturn((long) EmailVerificationStore.MAX_DAILY_SEND + 1);

			assertThat(store.incrementAndCheckDailySend("gildong@naver.com", PURPOSE)).isFalse();
		}
	}

	@Nested
	@DisplayName("1회용 토큰")
	class Token {

		private static final String TOKEN = "d4f1c0ab9e7b4c2f8a5d3e6b1c9f0a2d";

		@Test
		@DisplayName("발급한 토큰을 peek 하면 용도·이메일을 그대로 돌려주고 지우지 않는다")
		void peekToken_validToken_returnsTargetWithoutDeleting() {
			given(redis.opsForValue()).willReturn(valueOperations);
			given(valueOperations.get("email-verify-token:" + TOKEN)).willReturn("SIGN_UP:gildong@naver.com");

			EmailVerificationStore.VerifiedTarget target = store.peekToken(TOKEN);

			assertThat(target.purpose()).isEqualTo(VerificationPurpose.SIGN_UP);
			assertThat(target.email()).isEqualTo("gildong@naver.com");
			// peek 는 지우지 않는다 — 실제 소비는 DB 커밋 뒤 deleteToken() 이 한다
			verify(redis, never()).delete(anyString());
		}

		@Test
		@DisplayName("삭제가 실제로 일어난 경우에만 소비 흔적을 남긴다")
		void deleteToken_actuallyDeleted_marksUsed() {
			given(redis.delete("email-verify-token:" + TOKEN)).willReturn(true);
			given(redis.opsForValue()).willReturn(valueOperations);

			store.deleteToken(TOKEN);

			verify(valueOperations).set(
					eq("email-verify-token-used:" + TOKEN), eq("1"), eq(EmailVerificationStore.TOKEN_TTL));
		}

		@Test
		@DisplayName("이미 지워진 토큰을 또 지우려 하면 소비 흔적을 새로 남기지 않는다")
		void deleteToken_alreadyGone_doesNotMarkUsedAgain() {
			// 두 요청이 동시에 peek 를 통과해도, 실제 삭제는 둘 중 하나만 성공한다
			given(redis.delete("email-verify-token:" + TOKEN)).willReturn(false);

			store.deleteToken(TOKEN);

			verify(redis, never()).opsForValue();
		}
	}
}
