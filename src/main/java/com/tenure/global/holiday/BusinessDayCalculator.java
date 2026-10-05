package com.tenure.global.holiday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 영업일 계산. 주말과 공휴일을 뺀다 — settlement 의 '구매 확정일 + 2영업일' 계산에 쓴다.
 */
@Component
@RequiredArgsConstructor
public class BusinessDayCalculator {

	private final HolidayStore holidayStore;

	/** {@code start} 로부터 영업일로 {@code businessDays} 만큼 더한 날짜. 당일은 세지 않는다 */
	public LocalDate plusBusinessDays(LocalDate start, int businessDays) {
		LocalDate date = start;
		int remaining = businessDays;
		while (remaining > 0) {
			date = date.plusDays(1);
			if (isBusinessDay(date)) {
				remaining--;
			}
		}
		return date;
	}

	private boolean isBusinessDay(LocalDate date) {
		if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
			return false;
		}
		return !holidayStore.getHolidays(YearMonth.from(date)).contains(date);
	}
}
