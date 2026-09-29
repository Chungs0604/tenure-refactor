package com.tenure.global.pagination;

import java.util.List;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 커서 조회 결과 한 조각. Service 가 돌려주는 타입이며 아직 응답이 아니다.
 *
 * 커서를 문자열이 아니라 값으로 들고 있다. 문자열로 바꾸는 일은 전송 계층의 몫이라
 * Controller 가 CursorCodec 으로 처리한다 — Service 는 base64 를 알 필요가 없고,
 * 단위 테스트도 인코딩된 문자열 대신 정렬 키를 그대로 확인한다.
 *
 * 리포지토리는 size + 1 건을 읽는다. 한 건 더 읽는 것으로 다음 페이지 유무를 알 수 있어
 * 전체를 세지 않는다. 그 여분 한 건은 화면에 나가지 않으므로 of 가 잘라낸다.
 */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CursorSlice<E> {

	private final List<E> content;

	private final Boolean hasNext;

	/** 마지막 페이지면 null */
	private final Cursor nextCursor;

	/**
	 * 읽어 온 행에서 여분 한 건을 잘라내고 다음 커서를 만든다.
	 *
	 * rows — 요청한 size 보다 한 건 많을 수 있다
	 * cursorOf — 화면에 나가는 마지막 행 → 커서. 잘라낸 행으로 만들면 그 한 건이 영영 안 보인다
	 */
	public static <E> CursorSlice<E> of(List<E> rows, int size, Function<E, Cursor> cursorOf) {
		boolean hasNext = rows.size() > size;
		List<E> content = hasNext ? List.copyOf(rows.subList(0, size)) : rows;

		return CursorSlice.<E>builder()
				.content(content)
				.hasNext(hasNext)
				.nextCursor(hasNext ? cursorOf.apply(content.get(content.size() - 1)) : null)
				.build();
	}

	public static <E> CursorSlice<E> empty() {
		return CursorSlice.<E>builder()
				.content(List.of())
				.hasNext(false)
				.nextCursor(null)
				.build();
	}

	/** 조회한 엔티티를 응답 DTO 로 바꾼다. 커서는 그대로 따라간다. */
	public <T> CursorSlice<T> map(Function<E, T> mapper) {
		return CursorSlice.<T>builder()
				.content(content.stream().map(mapper).toList())
				.hasNext(hasNext)
				.nextCursor(nextCursor)
				.build();
	}

	public boolean isEmpty() {
		return content.isEmpty();
	}
}
