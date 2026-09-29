package com.tenure.domain.item.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 'Tenure 기록 기준' 섹션 전체. 구매자 시점의 상품 상세도 같은 응답을 쓴다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "Tenure 기록 기준")
public class ItemOwnershipHistoryResponse {

	@Schema(description = "소유 구간. 최신 구간이 먼저 온다")
	private final List<ItemOwnershipResponse> ownerships;

	@Schema(description = "전체 인증 횟수. 구간 합계이며 무효화된 기록은 빠진다", example = "20")
	private final Integer totalWearCount;

	@Schema(description = "최초 보유 날짜. 입력하지 않았으면 null", example = "2026-04-20")
	private final LocalDate firstOwnedDate;

	@Schema(description = "최근 착용일. 인증이 없으면 null", example = "2026-05-26")
	private final LocalDate recentWornDate;
}
