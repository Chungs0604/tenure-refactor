package com.tenure.global.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BusinessDayCalculator")
class BusinessDayCalculatorTest {

	@Mock private HolidayStore holidayStore;

	@InjectMocks private BusinessDayCalculator calculator;

	@Test
	@DisplayName("공휴일·주말이 없으면 평일만 센다")
	void plusBusinessDays_noHolidays_countsOnlyWeekdays() {
		// 2026-06-10(수) + 2영업일 = 06-12(금)
		given(holidayStore.getHolidays(any())).willReturn(Set.of());

		LocalDate result = calculator.plusBusinessDays(LocalDate.of(2026, 6, 10), 2);

		assertThat(result).isEqualTo(LocalDate.of(2026, 6, 12));
	}

	@Test
	@DisplayName("주말을 건너뛴다")
	void plusBusinessDays_skipsWeekend() {
		// 2026-06-12(금) + 1영업일 = 토·일을 건너뛰고 06-15(월)
		given(holidayStore.getHolidays(any())).willReturn(Set.of());

		LocalDate result = calculator.plusBusinessDays(LocalDate.of(2026, 6, 12), 1);

		assertThat(result).isEqualTo(LocalDate.of(2026, 6, 15));
	}

	@Test
	@DisplayName("공휴일을 건너뛴다")
	void plusBusinessDays_skipsHoliday() {
		// 2026-06-10(수) + 1영업일인데 06-11(목)이 공휴일이면 06-12(금)
		LocalDate holiday = LocalDate.of(2026, 6, 11);
		given(holidayStore.getHolidays(YearMonth.from(holiday))).willReturn(Set.of(holiday));

		LocalDate result = calculator.plusBusinessDays(LocalDate.of(2026, 6, 10), 1);

		assertThat(result).isEqualTo(LocalDate.of(2026, 6, 12));
	}

	@Test
	@DisplayName("월을 넘어가면 그 달의 공휴일을 다시 조회한다")
	void plusBusinessDays_crossesMonth_queriesEachMonth() {
		// 2026-06-30(화) + 1영업일 = 07-01(수). 7월 공휴일 조회가 걸린다
		LocalDate julyHoliday = LocalDate.of(2026, 7, 1);
		given(holidayStore.getHolidays(YearMonth.of(2026, 6))).willReturn(Set.of());
		given(holidayStore.getHolidays(YearMonth.of(2026, 7))).willReturn(Set.of(julyHoliday));

		LocalDate result = calculator.plusBusinessDays(LocalDate.of(2026, 6, 30), 1);

		assertThat(result).isEqualTo(LocalDate.of(2026, 7, 2));
	}

	@Test
	@DisplayName("0 영업일을 더하면 시작일 그대로다")
	void plusBusinessDays_zero_returnsStart() {
		LocalDate start = LocalDate.of(2026, 6, 10);

		assertThat(calculator.plusBusinessDays(start, 0)).isEqualTo(start);
	}
}
