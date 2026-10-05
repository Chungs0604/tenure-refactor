package com.tenure.global.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Redis 캐시 인코딩·디코딩. 실제 Redis 연결 대신 StringRedisTemplate 을 모킹한다 —
 * 프로젝트에 Redis 를 쓰는 기존 슬라이스 테스트 패턴이 없어, 외부 연동(Redis)을 모킹하는
 * Service 단위 테스트 방식으로 캐시 적중/미스 분기와 인코딩 규칙만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HolidayStore")
class HolidayStoreTest {

	@Mock private StringRedisTemplate redis;
	@Mock private ValueOperations<String, String> valueOperations;
	@Mock private HolidayApiClient holidayApiClient;

	@InjectMocks private HolidayStore holidayStore;

	@Test
	@DisplayName("캐시가 비어 있으면 API 를 불러 채우고 콤마로 이어 담는다")
	void getHolidays_cacheMiss_fetchesAndCaches() {
		YearMonth month = YearMonth.of(2026, 6);
		LocalDate holiday = LocalDate.of(2026, 6, 6);
		given(redis.opsForValue()).willReturn(valueOperations);
		given(valueOperations.get("holiday:2026-06")).willReturn(null);
		given(holidayApiClient.fetchHolidays(month)).willReturn(Optional.of(Set.of(holiday)));

		Set<LocalDate> result = holidayStore.getHolidays(month);

		assertThat(result).containsExactly(holiday);
		verify(valueOperations).set(eq("holiday:2026-06"), eq("20260606"), eq(Duration.ofDays(180)));
	}

	@Test
	@DisplayName("캐시가 있으면 API 를 다시 부르지 않는다")
	void getHolidays_cacheHit_doesNotCallApi() {
		YearMonth month = YearMonth.of(2026, 6);
		given(redis.opsForValue()).willReturn(valueOperations);
		given(valueOperations.get("holiday:2026-06")).willReturn("20260606,20260817");

		Set<LocalDate> result = holidayStore.getHolidays(month);

		assertThat(result).containsExactlyInAnyOrder(LocalDate.of(2026, 6, 6), LocalDate.of(2026, 8, 17));
		verify(holidayApiClient, never()).fetchHolidays(any());
	}

	@Test
	@DisplayName("정상 조회인데 그 달에 공휴일이 없으면 빈 마커로 180일 캐시한다")
	void getHolidays_legitimatelyEmptyMonth_cachesLongTtl() {
		YearMonth month = YearMonth.of(2026, 9);
		given(redis.opsForValue()).willReturn(valueOperations);
		given(valueOperations.get("holiday:2026-09")).willReturn(null);
		given(holidayApiClient.fetchHolidays(month)).willReturn(Optional.of(Set.of()));

		Set<LocalDate> result = holidayStore.getHolidays(month);

		assertThat(result).isEmpty();
		verify(valueOperations).set(eq("holiday:2026-09"), eq("-"), eq(Duration.ofDays(180)));
	}

	@Test
	@DisplayName("API 호출이 실패하면 fail-open 으로 빈 집합을 주되 짧게만 캐시한다")
	void getHolidays_apiFails_failsOpenWithShortTtl() {
		YearMonth month = YearMonth.of(2026, 9);
		given(redis.opsForValue()).willReturn(valueOperations);
		given(valueOperations.get("holiday:2026-09")).willReturn(null);
		given(holidayApiClient.fetchHolidays(month)).willReturn(Optional.empty());

		Set<LocalDate> result = holidayStore.getHolidays(month);

		assertThat(result).isEmpty();
		verify(valueOperations).set(eq("holiday:2026-09"), eq("-"), eq(Duration.ofMinutes(10)));
	}

	@Test
	@DisplayName("빈 마커로 캐시된 달은 빈 집합으로 해석한다 — API 를 다시 부르지 않는다")
	void getHolidays_emptyMarkerCached_returnsEmptySet() {
		YearMonth month = YearMonth.of(2026, 9);
		given(redis.opsForValue()).willReturn(valueOperations);
		given(valueOperations.get("holiday:2026-09")).willReturn("-");

		Set<LocalDate> result = holidayStore.getHolidays(month);

		assertThat(result).isEmpty();
		verify(holidayApiClient, never()).fetchHolidays(any());
	}

	@Test
	@DisplayName("캐시가 빈 문자열이어도 빈 집합으로 해석한다")
	void getHolidays_blankCached_returnsEmptySet() {
		YearMonth month = YearMonth.of(2026, 9);
		given(redis.opsForValue()).willReturn(valueOperations);
		given(valueOperations.get("holiday:2026-09")).willReturn("");

		Set<LocalDate> result = holidayStore.getHolidays(month);

		assertThat(result).isEmpty();
	}
}
