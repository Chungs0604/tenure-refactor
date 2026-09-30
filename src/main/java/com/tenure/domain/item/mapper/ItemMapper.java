package com.tenure.domain.item.mapper;

import com.tenure.domain.item.dto.response.ItemHistoryPostResponse;
import com.tenure.domain.item.dto.response.ItemIdResponse;
import com.tenure.domain.item.dto.response.ItemListingSummaryResponse;
import com.tenure.domain.item.dto.response.ItemOwnershipHistoryResponse;
import com.tenure.domain.item.dto.response.ItemOwnershipResponse;
import com.tenure.domain.item.dto.response.ItemRecommendationResponse;
import com.tenure.domain.item.dto.response.ItemResponse;
import com.tenure.domain.item.dto.response.ItemSummaryResponse;
import com.tenure.domain.item.dto.response.TaggableItemResponse;
import com.tenure.domain.item.dto.response.ItemWornWithResponse;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.RecommendationBlock;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.listing.enums.SaleStatus;
import com.tenure.domain.post.dto.response.PostSummaryResponse;
import com.tenure.domain.post.enums.AspectRatio;
import java.time.LocalDate;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 엔티티 → 응답 단방향 변환.
 *
 * 저장소 키를 URL 로 바꾸는 일은 여기서 하지 않는다 — 매핑 계층이 FileStorage 를 알면
 * 단위 테스트가 저장소 설정에 묶인다. Service 가 바꿔서 넘긴 값을 그대로 싣는다.
 * 인증 횟수·최근 착용일처럼 페이지 단위로 한 번에 조회한 값도 같은 이유로 인자로 받는다.
 *
 * unmappedTargetPolicy 가 ERROR 다 — 응답에 필드를 더하고 채우는 것을 잊으면 빌드가 막는다.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ItemMapper {

	@Mapping(target = "id", source = "item.id")
	ItemIdResponse toItemIdResponse(Item item);

	/** 내 옷장 목록의 한 행. 화면이 '미판매 · 최근 착용 1일 전' 과 판매 전환 버튼을 그린다. */
	@Mapping(target = "id", source = "item.id")
	@Mapping(target = "brandName", source = "item.brandName")
	@Mapping(target = "name", source = "item.name")
	@Mapping(target = "coverImageUrl", source = "coverImageUrl")
	@Mapping(target = "saleStatus", source = "saleStatus")
	@Mapping(target = "lastWornDate", source = "lastWornDate")
	@Mapping(target = "listing", source = "listing")
	ItemSummaryResponse toItemSummaryResponse(
			Item item, String coverImageUrl, SaleStatus saleStatus,
			LocalDate lastWornDate, ItemListingSummaryResponse listing
	);

	/** 태그 시트의 한 행. 화면이 '최근 착용 7일 전 · OOTD 인증 : 2회' 를 그린다. */
	@Mapping(target = "id", source = "item.id")
	@Mapping(target = "brandName", source = "item.brandName")
	@Mapping(target = "name", source = "item.name")
	@Mapping(target = "coverImageUrl", source = "coverImageUrl")
	@Mapping(target = "lastWornDate", source = "lastWornDate")
	@Mapping(target = "wearCount", source = "wearCount")
	TaggableItemResponse toTaggableItemResponse(
			Item item, String coverImageUrl, LocalDate lastWornDate, Integer wearCount
	);

	/** 아이템 상세. 화면이 '상의 · 긴소매 티셔츠' 로 두 단계를 함께 그려 대분류 이름도 싣는다. */
	@Mapping(target = "id", source = "item.id")
	@Mapping(target = "name", source = "item.name")
	@Mapping(target = "brandName", source = "item.brandName")
	@Mapping(target = "sizeLabel", source = "item.sizeLabel")
	@Mapping(target = "wearTarget", source = "item.wearTarget")
	@Mapping(target = "firstOwnedDate", source = "item.firstOwnedDate")
	@Mapping(target = "wishCount", source = "item.wishCount")
	@Mapping(target = "offerAllowed", source = "item.offerAllowed")
	@Mapping(target = "rootCategoryName", source = "item.category.parent.name")
	@Mapping(target = "categoryId", source = "item.category.id")
	@Mapping(target = "categoryName", source = "item.category.name")
	@Mapping(target = "coverImageUrl", source = "coverImageUrl")
	@Mapping(target = "saleStatus", source = "saleStatus")
	@Mapping(target = "ownerships", source = "ownerships")
	@Mapping(target = "frequentlyWornWith", source = "frequentlyWornWith")
	@Mapping(target = "recentWornDate", source = "recentWornDate")
	@Mapping(target = "totalWearCount", source = "totalWearCount")
	@Mapping(target = "listing", source = "listing")
	ItemResponse toItemResponse(
			Item item, String coverImageUrl, SaleStatus saleStatus,
			List<ItemOwnershipResponse> ownerships, List<ItemWornWithResponse> frequentlyWornWith,
			LocalDate recentWornDate, Integer totalWearCount, ItemListingSummaryResponse listing
	);

	/** 판매 조건. 판매 등록이 없으면 통째로 null 이다. */
	@Mapping(target = "id", source = "listing.id")
	@Mapping(target = "price", source = "listing.price")
	ItemListingSummaryResponse toListingSummaryResponse(ItemListing listing);

	/** 소유 구간 카드. 인증 횟수와 미리보기 사진은 구간마다 따로 세어 넘긴다. */
	@Mapping(target = "startDate", source = "ownership.startDate")
	@Mapping(target = "endDate", source = "ownership.endDate")
	@Mapping(target = "acquiredBy", source = "ownership.acquiredBy")
	@Mapping(target = "ownerNickname", source = "ownerNickname")
	@Mapping(target = "ownerProfileImageUrl", source = "ownerProfileImageUrl")
	@Mapping(target = "wearCount", source = "wearCount")
	@Mapping(target = "previewImages", source = "previewImages")
	ItemOwnershipResponse toItemOwnershipResponse(
			ItemOwnership ownership, String ownerNickname, String ownerProfileImageUrl,
			Integer wearCount, List<String> previewImages
	);

	ItemOwnershipHistoryResponse toItemOwnershipHistoryResponse(
			List<ItemOwnershipResponse> ownerships, Integer totalWearCount,
			LocalDate firstOwnedDate, LocalDate recentWornDate
	);

	/** '자주 같이 입은 옷' 칩. */
	@Mapping(target = "itemId", source = "item.id")
	@Mapping(target = "brandName", source = "item.brandName")
	@Mapping(target = "name", source = "item.name")
	ItemWornWithResponse toItemWornWithResponse(Item item);

	/** 히스토리 그리드의 사진 한 장. 조회 결과가 배열이라 값으로 받는다. */
	ItemHistoryPostResponse toItemHistoryPostResponse(
			Long postId, String imageUrl, AspectRatio aspectRatio, LocalDate wornDate
	);

	/** 추천 블록. 제목은 기준 아이템 이름이 들어가는 경우가 있어 Service 가 만들어 넘긴다. */
	@Mapping(target = "block", source = "block")
	ItemRecommendationResponse toItemRecommendationResponse(
			RecommendationBlock block, String title, String description,
			List<PostSummaryResponse> posts
	);
}
