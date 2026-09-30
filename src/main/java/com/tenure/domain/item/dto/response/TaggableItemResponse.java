package com.tenure.domain.item.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * OOTD 를 올릴 때 사진에 태그할 아이템을 고르는 시트의 한 행.
 *
 * 행은 '브랜드 / 아이템명' 아래에 '최근 착용 7일 전 · OOTD 인증 : 2회' 를 그린다.
 * 판매 상태를 그리지 않으므로 내 옷장(ItemSummaryResponse)과 DTO 를 나눴다 —
 * 한 화면이 안 쓰는 칸을 null 로 싣지 않기 위해서다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "태그 가능한 아이템")
public class TaggableItemResponse {

	@Schema(description = "아이템 id", example = "1")
	private final Long id;

	@Schema(description = "브랜드명", example = "Resolute")
	private final String brandName;

	@Schema(description = "아이템명", example = "710 Denim")
	private final String name;

	@Schema(description = "대표 사진 URL")
	private final String coverImageUrl;

	@Schema(description = "'최근 착용 N일 전' 계산 기준. 인증이 없으면 null", example = "2026-05-26")
	private final LocalDate lastWornDate;

	@Schema(description = "'OOTD 인증 : N회'. 현재 소유 구간 기준", example = "2")
	private final Integer wearCount;
}
