package com.tenure.global.holiday;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 월별 공휴일 캐시. 같은 달을 정산 배치가 매일 다시 조회하지 않도록 Redis 에 담아 둔다.
 *
 * 값이 빈 문자열이어도(공휴일 없는 달) 캐시 적중으로 본다 — null 과 '빈 집합' 을 구분해야
 * 매번 외부 API 를 다시 두드리지 않는다. 그래서 '없음'을 별도 마커로 담는다.
 *
 * API 호출이 실패한 결과는 **짧게만** 캐시한다 — 180일로 캐시하면 일시 장애가 그 달 내내
 * '공휴일 없음'으로 고정돼버린다. 캐시를 두는 목적은 정상 응답을 재사용하는 것이지
 * 장애를 오래 전파하는 것이 아니다
 */
@Component
@RequiredArgsConstructor
public class HolidayStore {

	private static final String KEY = "holiday:%s";
	private static final String EMPTY_MARKER = "-";
	private static final String DELIMITER = ",";
	private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

	/** 지난 달 공휴일이 바뀔 일은 없으니 넉넉히 180일 — 정상 응답에만 적용 */
	private static final long TTL_DAYS = 180;

	/** 조회 실패는 다음 호출에서 금방 다시 시도할 수 있게 짧게만 캐시함(같은 틱에서 재시도 폭주만 막음) */
	private static final Duration FAILURE_TTL = Duration.ofMinutes(10);

	private final StringRedisTemplate redis;
	private final HolidayApiClient holidayApiClient;

	/** 이 월의 공휴일 날짜 집합. 캐시 미스면 API 를 불러 채운다. 조회 실패 시에도 빈 집합을 돌려주지만(fail-open) 길게 캐시하지 않는다 */
	public Set<LocalDate> getHolidays(YearMonth month) {
		String key = KEY.formatted(month);
		String cached = redis.opsForValue().get(key);
		if (cached != null) {
			return decode(cached);
		}

		Optional<Set<LocalDate>> fetched = holidayApiClient.fetchHolidays(month);
		if (fetched.isPresent()) {
			redis.opsForValue().set(key, encode(fetched.get()), Duration.ofDays(TTL_DAYS));
			return fetched.get();
		}

		// 실패 — fail-open(공휴일 없음으로 간주)하되 장애가 오래 전파되지 않게 짧게만 캐시
		redis.opsForValue().set(key, EMPTY_MARKER, FAILURE_TTL);
		return Set.of();
	}

	private String encode(Set<LocalDate> holidays) {
		if (holidays.isEmpty()) {
			return EMPTY_MARKER;
		}
		return holidays.stream().map(date -> date.format(DAY_FORMAT)).collect(Collectors.joining(DELIMITER));
	}

	private Set<LocalDate> decode(String cached) {
		if (!StringUtils.hasText(cached) || EMPTY_MARKER.equals(cached)) {
			return Set.of();
		}
		return Set.of(cached.split(DELIMITER)).stream()
				.map(raw -> LocalDate.parse(raw, DAY_FORMAT))
				.collect(Collectors.toSet());
	}
}
