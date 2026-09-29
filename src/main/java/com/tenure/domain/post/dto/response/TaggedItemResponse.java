package com.tenure.domain.post.dto.response;

import com.tenure.domain.post.enums.TaggedItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 사진 속 태그된 아이템 한 건.
 *
 * listingId 가 null 이면 판매 등록이 한 번도 없었던 아이템이라 CTA 버튼을 띄우지 않는다
 * ('구매제안 불가능 제품에 관하여는 CTA 버튼이 없다').
 *
 * 행 우측 토글은 하트가 아니라 벨이다. 관심 등록과 알림 신청을 겸하므로 상태값도 하나만 싣는다 —
 * 위시에 담지 않은 채 알림만 켜는 상태가 없다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "태그된 아이템")
public class TaggedItemResponse {

	@Schema(description = "아이템 id")
	private final Long itemId;

	@Schema(description = "최신 판매 등록 id. null 이면 CTA 없음")
	private final Long listingId;

	@Schema(description = "브랜드명", example = "Levis")
	private final String brandName;

	@Schema(description = "아이템명", example = "LVC 1955 501")
	private final String name;

	@Schema(description = "대표 사진 URL")
	private final String coverImageUrl;

	@Schema(description = "표시 상태", example = "ON_SALE")
	private final TaggedItemStatus status;

	@Schema(description = "판매가. 판매 등록이 없으면 null", example = "360000")
	private final String price;

	@Schema(description = "구매 제안 허용 여부")
	private final Boolean offerAllowed;

	@Schema(description = "알림을 받고 있으면 true. 행 우측 벨 아이콘의 상태다")
	private final Boolean alarmEnabled;
}
