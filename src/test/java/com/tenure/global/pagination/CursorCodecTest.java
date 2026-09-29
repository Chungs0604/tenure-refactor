package com.tenure.global.pagination;

import com.tenure.global.exception.CommonErrorCode;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CursorCodec")
class CursorCodecTest {

	private final CursorCodec cursorCodec = new CursorCodec();

	@Test
	@DisplayName("게시일 커서를 넣은 그대로 되돌려 받는다")
	void encodeDecode_dateTimeCursor_roundTrips() {
		LocalDateTime createdAt = LocalDateTime.of(2026, 6, 21, 18, 30, 12, 345_000_000);

		Cursor decoded = cursorCodec.decode(cursorCodec.encode(Cursor.of(createdAt, 10L)));

		assertThat(decoded.asDateTime()).isEqualTo(createdAt);
		assertThat(decoded.getId()).isEqualTo(10L);
	}

	@Test
	@DisplayName("착용일 커서도 마찬가지다")
	void encodeDecode_dateCursor_roundTrips() {
		LocalDate wornDate = LocalDate.of(2026, 6, 21);

		Cursor decoded = cursorCodec.decode(cursorCodec.encode(Cursor.of(wornDate, 7L)));

		assertThat(decoded.asDate()).isEqualTo(wornDate);
		assertThat(decoded.getId()).isEqualTo(7L);
	}

	@Test
	@DisplayName("커서 문자열에서 값이 읽히지 않는다 — 클라이언트가 만들어 보내지 못하게 한다")
	void encode_always_isOpaque() {
		String encoded = cursorCodec.encode(Cursor.of(LocalDate.of(2026, 6, 21), 7L));

		assertThat(encoded).doesNotContain("2026-06-21").doesNotContain("|");
	}

	@Test
	@DisplayName("첫 페이지는 커서가 없다 — 빈 값이면 null 이다")
	void decode_blank_returnsNull() {
		assertThat(cursorCodec.decode(null)).isNull();
		assertThat(cursorCodec.decode("")).isNull();
		assertThat(cursorCodec.decode("   ")).isNull();
	}

	@Test
	@DisplayName("다음 페이지가 없으면 커서도 없다")
	void encode_null_returnsNull() {
		assertThat(cursorCodec.encode(null)).isNull();
	}

	@Test
	@DisplayName("base64 가 아니면 400 이다 — 조용히 첫 페이지를 주면 같은 목록이 반복해서 붙는다")
	void decode_notBase64_throwsBadRequest() {
		assertThatThrownBy(() -> cursorCodec.decode("!!not-base64!!"))
				.isInstanceOf(CustomException.class)
				.extracting(CursorCodecTest::errorCodeOf)
				.isEqualTo(CommonErrorCode.BAD_REQUEST);
	}

	@Test
	@DisplayName("형식이 맞지 않으면 400 이다")
	void decode_wrongShape_throwsBadRequest() {
		String encoded = encodeRaw("2026-06-21|7");

		assertThatThrownBy(() -> cursorCodec.decode(encoded))
				.isInstanceOf(CustomException.class)
				.extracting(CursorCodecTest::errorCodeOf)
				.isEqualTo(CommonErrorCode.BAD_REQUEST);
	}

	@Test
	@DisplayName("모르는 버전은 400 이다 — 형식을 바꿔도 옛 커서를 구분해 거부할 수 있다")
	void decode_unknownVersion_throwsBadRequest() {
		String encoded = encodeRaw("v9|2026-06-21|7");

		assertThatThrownBy(() -> cursorCodec.decode(encoded))
				.isInstanceOf(CustomException.class)
				.extracting(CursorCodecTest::errorCodeOf)
				.isEqualTo(CommonErrorCode.BAD_REQUEST);
	}

	@Test
	@DisplayName("id 가 숫자가 아니면 400 이다")
	void decode_nonNumericId_throwsBadRequest() {
		String encoded = encodeRaw("v1|2026-06-21|abc");

		assertThatThrownBy(() -> cursorCodec.decode(encoded))
				.isInstanceOf(CustomException.class)
				.extracting(CursorCodecTest::errorCodeOf)
				.isEqualTo(CommonErrorCode.BAD_REQUEST);
	}

	@Test
	@DisplayName("정렬 키가 날짜가 아니면 읽는 시점에 400 이다")
	void asDate_malformedSortKey_throwsBadRequest() {
		Cursor cursor = cursorCodec.decode(encodeRaw("v1|어제|7"));

		assertThatThrownBy(cursor::asDate)
				.isInstanceOf(CustomException.class)
				.extracting(CursorCodecTest::errorCodeOf)
				.isEqualTo(CommonErrorCode.BAD_REQUEST);
	}


	@Test
	@DisplayName("조회 결과를 응답으로 바꾼다 — 커서를 문자열로 만드는 일은 전송 계층의 몫이다")
	void toResponse_hasNext_encodesCursor() {
		// 실제 조회 경로대로 만든다 — 3건을 읽고 2건만 화면에 내보내는 상황이다
		CursorSlice<String> slice = CursorSlice.of(
				List.of("a", "b", "c"), 2, row -> Cursor.of(LocalDate.of(2026, 6, 21), 7L));

		CursorResponse<String> response = cursorCodec.toResponse(slice);

		assertThat(response.getContent()).containsExactly("a", "b");
		assertThat(response.getHasNext()).isTrue();
		assertThat(cursorCodec.decode(response.getNextCursor()).getId()).isEqualTo(7L);
	}

	@Test
	@DisplayName("마지막 페이지면 응답의 커서가 null 이다")
	void toResponse_lastPage_hasNullCursor() {
		CursorResponse<String> response = cursorCodec.toResponse(CursorSlice.empty());

		assertThat(response.getContent()).isEmpty();
		assertThat(response.getHasNext()).isFalse();
		assertThat(response.getNextCursor()).isNull();
	}

	private static String encodeRaw(String raw) {
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
