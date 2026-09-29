package com.tenure.global.pagination;

import com.tenure.global.exception.CommonErrorCode;
import com.tenure.global.exception.CustomException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 커서가 가리키는 위치. 마지막으로 내려준 행의 정렬 키와 id 다.
 *
 * id 를 함께 갖는 이유는 정렬 키가 겹치기 때문이다 — 같은 착용일 게시물이 여러 개이면
 * 정렬 키만으로는 어디까지 줬는지 알 수 없어 경계에서 건너뛰거나 겹친다.
 *
 * 정렬 키를 문자열로 들고 있다. 목록마다 타입이 다르고(게시일 LocalDateTime ·
 * 착용일 LocalDate) 커서는 그걸 그대로 실어 보내면 되므로, 해석은 읽는 쪽이 한다.
 *
 * equals·hashCode 를 만든다 — 같은 위치를 가리키면 같은 커서다. 엔티티가 아니라
 * 값이므로 id 동일성으로 따질 대상이 아니고, 해독한 커서와 만든 커서를 비교할 일이 실제로 있다.
 */
@Getter
@EqualsAndHashCode
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Cursor {

	/** 정렬 키의 ISO 표기 */
	private final String sortKey;

	/** 같은 정렬 키 안에서의 tie-break */
	private final Long id;

	public static Cursor of(LocalDateTime sortValue, Long id) {
		return Cursor.builder()
				.sortKey(sortValue.toString())
				.id(id)
				.build();
	}

	public static Cursor of(LocalDate sortValue, Long id) {
		return Cursor.builder()
				.sortKey(sortValue.toString())
				.id(id)
				.build();
	}

	/**
	 * 인코딩된 문자열에서 되살린다. 정렬 키가 이미 ISO 표기라 그대로 담는다.
	 *
	 * 같은 패키지의 CursorCodec 만 쓴다 — 도메인 코드는 타입이 있는 팩토리를 쓴다.
	 */
	static Cursor of(String sortKey, Long id) {
		return Cursor.builder()
				.sortKey(sortKey)
				.id(id)
				.build();
	}

	public LocalDateTime asDateTime() {
		try {
			return LocalDateTime.parse(sortKey);
		} catch (DateTimeParseException e) {
			throw new CustomException(CommonErrorCode.BAD_REQUEST);
		}
	}

	public LocalDate asDate() {
		try {
			return LocalDate.parse(sortKey);
		} catch (DateTimeParseException e) {
			throw new CustomException(CommonErrorCode.BAD_REQUEST);
		}
	}
}
