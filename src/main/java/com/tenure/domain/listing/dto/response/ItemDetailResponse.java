package com.tenure.domain.listing.dto.response;

import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.offer.enums.OfferStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 아이템 상세 (공개 페이지). 판매중·미판매·판매완료가 한 화면의 상태 변형이다.
 *
 * 조회 키가 itemId 인 이유는 판매 전환을 한 번도 안 한 아이템에도 이 화면이 있어서다
 * (2825:58233). 그런 아이템에는 item_listing 행이 아예 없어 listingId 로는 열 수 없다.
 *
 * featuredPosts 와 taggedPosts 는 배타적이다 — 판매 등록 이력이 있으면 대표 OOTD 를 그리고
 * (미판매로 내려도 남는다, 2825:60351), 한 번도 없었으면 태그된 OOTD 로 대체한다.
 * 서버가 둘 중 하나만 채우고 나머지는 빈 배열로 내려준다.
 *
 * 소유 구간(ownerships)은 싣지 않는다 — 이 화면의 'Tenure 기록 기준' 은 6칸 그리드뿐이고
 * 소유 구간 카드는 소유자 시점(API-093·API-097)의 '아이템 히스토리' 에만 있다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "아이템 상세")
public class ItemDetailResponse {

	@Schema(description = "아이템 id. 관심·채팅·신고 호출 키", example = "1")
	private final Long itemId;

	@Schema(description = "브랜드명", example = "Levis")
	private final String brandName;

	@Schema(description = "아이템명", example = "LVC 1955 501")
	private final String name;

	@Schema(description = "상품 사진")
	private final List<String> images;

	@Schema(description = "판매 상태", example = "ON_SALE")
	private final SaleStatus saleStatus;

	@Schema(description = "구매 제안 허용. false 면 CTA 가 비활성으로 남는다", example = "true")
	private final Boolean offerAllowed;

	@Schema(description = "'관심 N명'", example = "5")
	private final Integer wishCount;

	@Schema(description = "내가 관심 등록했는지. 히어로 우측 벨 상태", example = "true")
	private final Boolean isWished;

	@Schema(description = "카테고리명", example = "긴소매 티셔츠")
	private final String categoryName;

	@Schema(description = "사이즈", example = "L")
	private final String sizeLabel;

	@Schema(description = "착용 대상", example = "MALE")
	private final WearTarget wearTarget;

	@Schema(description = "'최초 보유 날짜'", example = "2026-04-20")
	private final LocalDate firstOwnedDate;

	@Schema(description = "'OOTD 인증 N회'", example = "10")
	private final Integer totalWearCount;

	@Schema(description = "판매 조건. 열린 판매 등록이 없으면 null")
	private final ListingTermsResponse listing;

	@Schema(description = "판매자 정보. 판매 이력이 없으면 null")
	private final ListingSellerResponse seller;

	@Schema(description = "대표 OOTD. 판매 이력이 없으면 빈 배열")
	private final List<FeaturedPostResponse> featuredPosts;

	@Schema(description = "태그된 OOTD. 판매 이력이 있으면 빈 배열")
	private final List<FeaturedPostResponse> taggedPosts;

	@Schema(description = "내가 보낸 제안 상태. 없으면 null", example = "PENDING")
	private final OfferStatus myOfferStatus;
}
