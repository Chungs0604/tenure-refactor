package com.tenure.global.pagination;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 커서 목록 응답. BaseResponse 의 data 에 담긴다.
 *
 * 화면이 전부 아래로 스크롤하는 목록이라 페이지 번호가 필요 없다. 총 건수도 싣지 않는다 —
 * 세는 데 쿼리가 한 번 더 드는데 그 값을 쓰는 화면이 검색 결과 하나뿐이고, 거기서는 응답에
 * 총계를 따로 더한다.
 *
 * offset 방식이면 스크롤 중에 새 게시물이 앞에 들어올 때 페이지가 밀려 같은 항목이 두 번
 * 보이거나 한 건이 빠진다. 커서는 "어디까지 줬는지" 를 값으로 기억하므로 그 일이 없다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "커서 목록 응답")
public class CursorResponse<T> {

	@Schema(description = "목록")
	private final List<T> content;

	@Schema(description = "다음 페이지 커서. 다음 요청에 그대로 넘긴다. 마지막 페이지면 null")
	private final String nextCursor;

	@Schema(description = "다음 페이지 존재 여부", example = "true")
	private final Boolean hasNext;
}
