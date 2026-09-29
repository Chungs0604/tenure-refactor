package com.tenure.global.pagination;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CursorSlice")
class CursorSliceTest {

	/** 정렬 키가 날짜인 목록을 흉내낸다 — 숫자를 그날의 id 로 쓴다. */
	private static Cursor cursorOf(Integer row) {
		return Cursor.of(LocalDate.of(2026, 6, row), row.longValue());
	}

	@Test
	@DisplayName("한 건 더 읽혔으면 다음 페이지가 있고 그 한 건은 잘라낸다")
	void of_oneMoreRow_hasNextAndTrims() {
		CursorSlice<Integer> slice = CursorSlice.of(List.of(1, 2, 3), 2, CursorSliceTest::cursorOf);

		assertThat(slice.getHasNext()).isTrue();
		assertThat(slice.getContent()).containsExactly(1, 2);
	}

	@Test
	@DisplayName("커서는 화면에 나가는 마지막 행으로 만든다 — 잘라낸 행으로 만들면 그 한 건이 영영 안 보인다")
	void of_oneMoreRow_cursorPointsToLastVisibleRow() {
		CursorSlice<Integer> slice = CursorSlice.of(List.of(1, 2, 3), 2, CursorSliceTest::cursorOf);

		assertThat(slice.getNextCursor().getId()).isEqualTo(2L);
	}

	@Test
	@DisplayName("딱 맞게 읽혔으면 마지막 페이지이고 커서가 없다")
	void of_exactSize_hasNoNextCursor() {
		CursorSlice<Integer> slice = CursorSlice.of(List.of(1, 2), 2, CursorSliceTest::cursorOf);

		assertThat(slice.getHasNext()).isFalse();
		assertThat(slice.getNextCursor()).isNull();
		assertThat(slice.getContent()).containsExactly(1, 2);
	}

	@Test
	@DisplayName("결과가 없으면 비어 있다")
	void of_empty_isEmpty() {
		CursorSlice<Integer> slice = CursorSlice.of(List.of(), 20, CursorSliceTest::cursorOf);

		assertThat(slice.isEmpty()).isTrue();
		assertThat(slice.getHasNext()).isFalse();
	}

	@Test
	@DisplayName("응답 DTO 로 바꿔도 커서는 그대로 따라간다")
	void map_always_keepsCursor() {
		CursorSlice<Integer> slice = CursorSlice.of(List.of(1, 2, 3), 2, CursorSliceTest::cursorOf);

		CursorSlice<String> mapped = slice.map(String::valueOf);

		assertThat(mapped.getContent()).containsExactly("1", "2");
		assertThat(mapped.getHasNext()).isTrue();
		assertThat(mapped.getNextCursor()).isEqualTo(slice.getNextCursor());
	}
}
