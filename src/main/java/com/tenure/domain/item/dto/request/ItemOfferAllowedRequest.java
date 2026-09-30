package com.tenure.domain.item.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구매 제안 허용 토글.
 *
 * 상세 수정 시트가 아니라 선택 모달(3910:43234)의 항목이다 — 라벨이 상태에 따라
 * '구매 제안 허용' ↔ '구매 제안 차단'(3910:43454)으로 바뀐다. 아이템이 갖는 값이라
 * 판매 상태 변경과도 분리한다 (DEC-024).
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "구매 제안 허용 토글 요청")
public class ItemOfferAllowedRequest {

	@Schema(description = "구매 제안 허용 여부", example = "true")
	@NotNull
	private Boolean offerAllowed;
}
