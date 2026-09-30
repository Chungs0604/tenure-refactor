package com.tenure.domain.item.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 판매 조건. 판매 등록이 없으면 통째로 null 이다.
 *
 * 관심 수와 구매 제안 허용은 여기 없다 — 판매와 무관하게 살아 있어야 해서 아이템이 갖는다 (DEC-024).
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "판매 조건")
public class ItemListingSummaryResponse {

	@Schema(description = "판매 등록 id. 판매 정보 수정·판매 상태 변경의 입력 키다", example = "1")
	private final Long id;

	@Schema(description = "가격. 미판매면 null", example = "360000")
	private final String price;
}
