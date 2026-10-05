package com.tenure.global.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;

/**
 * 공공데이터포털 특일 정보 응답 파싱.
 *
 * MockWebServer 등 외부 서버 흉내 도구가 의존성에 없어, HolidayApiClient 가 매번 새로 build()
 * 하는 RestClient.Builder 체인 자체를 모킹해 실제 네트워크 호출 없이 응답 형태만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HolidayApiClient")
class HolidayApiClientTest {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Mock private RestClient.Builder restClientBuilder;
	@Mock private RestClient restClient;
	@Mock private RestClient.RequestHeadersUriSpec uriSpec;
	@Mock private RestClient.ResponseSpec responseSpec;

	private HolidayApiClient holidayApiClient;

	/**
	 * @InjectMocks 를 쓰지 않는다 — HolidayApiProperties 는 record 라 mock 대상이 아닌데,
	 * @InjectMocks 는 @Mock/@Spy 로 선언되지 않은 생성자 인자에 null 을 꽂아 NPE 로 이어진다
	 */
	@BeforeEach
	void setUp() {
		HolidayApiProperties properties = new HolidayApiProperties("https://api.example.com", "test-service-key");
		holidayApiClient = new HolidayApiClient(restClientBuilder, properties);
	}

	@Test
	@DisplayName("공휴일이 1건이면 item 이 배열이 아니라 단일 객체로 와도 파싱한다")
	void fetchHolidays_singleItemAsObject_parsesIt() throws Exception {
		stubResponse("""
				{"response":{"body":{"items":{"item":
					{"locdate":"20260815","isHoliday":"Y"}
				}}}}""");

		Set<LocalDate> holidays = holidayApiClient.fetchHolidays(YearMonth.of(2026, 8)).orElseThrow();

		assertThat(holidays).containsExactly(LocalDate.of(2026, 8, 15));
	}

	@Test
	@DisplayName("여러 건이면 배열로 와도 전부 담는다")
	void fetchHolidays_multipleItemsAsArray_parsesAll() throws Exception {
		stubResponse("""
				{"response":{"body":{"items":{"item":[
					{"locdate":"20260815","isHoliday":"Y"},
					{"locdate":"20261003","isHoliday":"Y"}
				]}}}}""");

		Set<LocalDate> holidays = holidayApiClient.fetchHolidays(YearMonth.of(2026, 8)).orElseThrow();

		assertThat(holidays).containsExactlyInAnyOrder(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 10, 3));
	}

	@Test
	@DisplayName("isHoliday 가 N 인 항목(대체공휴일 아님 등)은 제외한다")
	void fetchHolidays_notHolidayFlag_excluded() throws Exception {
		stubResponse("""
				{"response":{"body":{"items":{"item":[
					{"locdate":"20260815","isHoliday":"Y"},
					{"locdate":"20260816","isHoliday":"N"}
				]}}}}""");

		Set<LocalDate> holidays = holidayApiClient.fetchHolidays(YearMonth.of(2026, 8)).orElseThrow();

		assertThat(holidays).containsExactly(LocalDate.of(2026, 8, 15));
	}

	@Test
	@DisplayName("응답이 null 이면 정상 조회로 보고(Optional.of) 빈 집합을 담는다")
	void fetchHolidays_nullBody_returnsPresentEmptySet() {
		stubResponse(null);

		Set<LocalDate> holidays = holidayApiClient.fetchHolidays(YearMonth.of(2026, 8)).orElseThrow();

		assertThat(holidays).isEmpty();
	}

	@Test
	@DisplayName("items.item 자체가 없으면 정상 조회로 보고(Optional.of) 빈 집합을 담는다")
	void fetchHolidays_missingItemNode_returnsPresentEmptySet() throws Exception {
		stubResponse("""
				{"response":{"body":{"items":{}}}}""");

		Set<LocalDate> holidays = holidayApiClient.fetchHolidays(YearMonth.of(2026, 8)).orElseThrow();

		assertThat(holidays).isEmpty();
	}

	@Test
	@DisplayName("호출 자체가 예외를 던지면 '모름'(Optional.empty) 으로 구분한다 — HolidayStore 가 짧게만 캐시하게 하기 위함")
	void fetchHolidays_clientThrows_returnsEmptyOptional() {
		given(restClientBuilder.build()).willThrow(new RuntimeException("연결 실패"));

		var holidays = holidayApiClient.fetchHolidays(YearMonth.of(2026, 8));

		assertThat(holidays).isEmpty();
	}

	@SuppressWarnings("unchecked")
	private void stubResponse(String json) {
		JsonNode body = toJsonNode(json);
		given(restClientBuilder.build()).willReturn(restClient);
		given(restClient.get()).willReturn(uriSpec);
		// URI 객체로 넘긴다 — 이중 인코딩을 피하려고 .uri(String) 대신 .uri(URI) 를 쓴다
		given(uriSpec.uri(any(URI.class))).willReturn(uriSpec);
		given(uriSpec.retrieve()).willReturn(responseSpec);
		given(responseSpec.body(JsonNode.class)).willReturn(body);
	}

	private JsonNode toJsonNode(String json) {
		if (json == null) {
			return null;
		}
		try {
			return OBJECT_MAPPER.readTree(json);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
