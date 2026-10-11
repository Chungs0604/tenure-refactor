package com.tenure.domain.wishlist.dto.response;

import com.tenure.domain.item.enums.SaleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "위시리스트 항목")
public class WishlistItemResponse {

	@Schema(description = "알림 on/off(API-123) 호출 키")
	private final Long wishlistId;

	@Schema(description = "관심 해제(API-122) 호출 키이자 아이템 상세(API-110) 이동 키")
	private final Long itemId;

	@Schema(description = "브랜드명")
	private final String brandName;

	@Schema(description = "아이템명")
	private final String name;

	@Schema(description = "대표 사진 URL")
	private final String coverImageUrl;

	@Schema(description = "가격. 활성 판매 등록이 없으면 null", example = "360000")
	private final String price;

	@Schema(description = "판매중 / 미판매 배지")
	private final SaleStatus saleStatus;

	@Schema(description = "판매자 닉네임. 판매 등록 이력이 없으면 null")
	private final String sellerNickname;

	@Schema(description = "'1일 전'. 판매 등록이 있으면 그 등록 시각, 없으면 아이템 등록 시각 기준", example = "1일 전")
	private final String listedAgo;

	@Schema(description = "알림 벨")
	private final Boolean alarmEnabled;

	@Schema(description = "'구매 제안' 버튼 노출 여부")
	private final Boolean offerAllowed;
}
