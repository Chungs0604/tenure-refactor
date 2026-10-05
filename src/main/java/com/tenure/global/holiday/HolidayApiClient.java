package com.tenure.global.holiday;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 공공데이터포털 특일 정보 서비스(getRestDeInfo) 호출. 월 단위로 공휴일 목록을 받아온다.
 *
 * 응답의 {@code item} 은 공휴일이 1건이면 배열이 아니라 객체로 온다 — 이 API 의 알려진 특이사항이라
 * JsonNode 로 느슨하게 읽는다.
 *
 * 호출 실패는 '공휴일 없음'과 구분한다 — 구분하지 않으면 HolidayStore 가 장애 결과를 180일짜리
 * 정상 결과로 캐시해버려, 일시 장애가 그 달 내내 영업일 계산을 틀리게 만든다(Optional.empty() = 실패,
 * Optional.of(빈 집합) = 정상 조회인데 그 달에 공휴일이 실제로 없음)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HolidayApiClient {

	private static final DateTimeFormatter LOCDATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

	private final RestClient.Builder restClientBuilder;
	private final HolidayApiProperties properties;

	/** 해당 월의 공휴일 날짜 목록. 조회 실패 시 빈 Optional(= '모름', '없음'과 다름) */
	public Optional<Set<LocalDate>> fetchHolidays(YearMonth month) {
		try {
			JsonNode body = restClientBuilder.build()
					.get()
					.uri(properties.baseUrl() + "/getRestDeInfo"
							+ "?serviceKey=" + properties.serviceKey()
							+ "&solYear=" + month.getYear()
							+ "&solMonth=" + "%02d".formatted(month.getMonthValue())
							+ "&numOfRows=100&_type=json")
					.retrieve()
					.body(JsonNode.class);
			return Optional.of(parse(body));
		} catch (Exception e) {
			log.warn("공휴일 조회에 실패함 — 이번 호출은 '모름'으로 처리하고 짧게만 캐시함 month={}", month, e);
			return Optional.empty();
		}
	}

	private Set<LocalDate> parse(JsonNode body) {
		Set<LocalDate> holidays = new HashSet<>();
		if (body == null) {
			return holidays;
		}

		JsonNode item = body.path("response").path("body").path("items").path("item");
		if (item.isMissingNode()) {
			return holidays;
		}

		// 공휴일이 1건이면 배열이 아니라 단일 객체로 온다
		if (item.isArray()) {
			item.forEach(node -> addIfHoliday(holidays, node));
		} else {
			addIfHoliday(holidays, item);
		}
		return holidays;
	}

	private void addIfHoliday(Set<LocalDate> holidays, JsonNode node) {
		if (!"Y".equals(node.path("isHoliday").asText())) {
			return;
		}
		holidays.add(LocalDate.parse(node.path("locdate").asText(), LOCDATE_FORMAT));
	}
}
