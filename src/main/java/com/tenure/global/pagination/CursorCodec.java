package com.tenure.global.pagination;

import com.tenure.global.exception.CommonErrorCode;
import com.tenure.global.exception.CustomException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 커서를 불투명한 문자열로 바꾸고 되돌린다.
 *
 * 암호화하지 않는다. 커서에 담기는 것은 정렬 키와 id 뿐이고 둘 다 이미 응답에 실려
 * 나가는 값이라 숨길 것이 없다. 목적은 클라이언트가 커서를 해석·조립하지 못하게 하는 것이다 —
 * 형식을 알면 클라이언트가 직접 만들어 보내기 시작하고, 그때부터 서버가 형식을 바꿀 수 없다.
 *
 * 버전을 앞에 붙여 두면 나중에 형식을 바꿔도 옛 커서를 구분해 거부할 수 있다.
 * 조작 방지(서명)는 두지 않았다 — 소유자·차단 같은 조건은 서버가 붙이므로 커서를 고쳐도
 * 남의 데이터가 나오지 않는다.
 */
@Slf4j
@Component
public class CursorCodec {

	private static final String VERSION = "v1";
	private static final String DELIMITER = "|";
	private static final int PART_COUNT = 3;

	/**
	 * 정상적인 커서는 이 안에 한참 못 미친다(가장 긴 정렬 키인 LocalDateTime ISO 표기도
	 * 30자 안팎). 길이 제한 없이 그대로 디코딩·파싱하면 공격자가 거대한 숫자 문자열을 커서로
	 * 보내 {@code BigDecimal}·{@code Long} 파싱 비용을 부풀리는 자원 소모(DoS) 통로가 된다 —
	 * Base64 디코드 전에 걸러낸다.
	 */
	private static final int MAX_ENCODED_LENGTH = 200;

	/**
	 * 조회 결과를 응답으로 바꾼다. Controller 가 부른다.
	 *
	 * 커서를 문자열로 만드는 일이 여기 있는 이유는 그것이 전송 형식이기 때문이다 —
	 * Service 는 정렬 키를 값으로 넘기고 base64 를 모른다.
	 */
	public <T> CursorResponse<T> toResponse(CursorSlice<T> slice) {
		return CursorResponse.<T>builder()
				.content(slice.getContent())
				.nextCursor(encode(slice.getNextCursor()))
				.hasNext(slice.getHasNext())
				.build();
	}

	/** 다음 페이지가 없으면 커서도 없다. */
	public String encode(Cursor cursor) {
		if (cursor == null) {
			return null;
		}

		String raw = String.join(DELIMITER, VERSION, cursor.getSortKey(), String.valueOf(cursor.getId()));
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * 첫 페이지는 커서가 없다 — 비어 있으면 null 을 돌려준다.
	 *
	 * 깨진 커서는 400 이다. 조용히 첫 페이지를 돌려주면 클라이언트가 무한 스크롤을 처음부터
	 * 다시 돌며 같은 목록을 반복해서 붙인다.
	 */
	public Cursor decode(String encoded) {
		if (!StringUtils.hasText(encoded)) {
			return null;
		}

		if (encoded.length() > MAX_ENCODED_LENGTH) {
			log.warn("커서가 너무 깁니다. length={}", encoded.length());
			throw new CustomException(CommonErrorCode.BAD_REQUEST);
		}

		String raw;
		try {
			raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			log.warn("커서를 해독할 수 없습니다. cursor={}", encoded);
			throw new CustomException(CommonErrorCode.BAD_REQUEST);
		}

		String[] parts = raw.split("\\" + DELIMITER, PART_COUNT);
		if (parts.length != PART_COUNT || !VERSION.equals(parts[0])) {
			log.warn("커서 형식이 맞지 않습니다. raw={}", raw);
			throw new CustomException(CommonErrorCode.BAD_REQUEST);
		}

		try {
			return Cursor.of(parts[1], Long.parseLong(parts[2]));
		} catch (NumberFormatException e) {
			log.warn("커서의 id 가 숫자가 아닙니다. raw={}", raw);
			throw new CustomException(CommonErrorCode.BAD_REQUEST);
		}
	}
}
