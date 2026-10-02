package com.tenure.domain.listing.mapper;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.enums.ConditionCode;
import com.tenure.domain.listing.dto.response.FeaturedPostResponse;
import com.tenure.domain.listing.dto.response.ItemDetailResponse;
import com.tenure.domain.listing.dto.response.ListingIdResponse;
import com.tenure.domain.listing.dto.response.ListingSellerResponse;
import com.tenure.domain.listing.dto.response.ListingTermsResponse;
import com.tenure.domain.listing.dto.response.SaleStatusResponse;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.offer.enums.OfferStatus;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.user.entity.User;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import com.tenure.domain.category.enums.MeasurementField;
import com.tenure.domain.category.enums.MeasurementScheme;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 엔티티 → 응답 단방향 변환.
 *
 * 저장소 키를 URL 로 바꾸는 일은 여기서 하지 않는다 — 매핑 계층이 FileStorage 를 알면
 * 단위 테스트가 저장소 설정에 묶인다. Service 가 바꿔서 넘긴 값을 그대로 싣는다.
 * 금액도 Service 가 문자열로 바꿔 넘긴다.
 *
 * unmappedTargetPolicy 가 ERROR 다 — 응답에 필드를 더하고 채우는 것을 잊으면 빌드가 막는다.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ListingMapper {

	@Mapping(target = "id", source = "listing.id")
	ListingIdResponse toListingIdResponse(ItemListing listing);

	@Mapping(target = "itemId", source = "item.id")
	@Mapping(target = "saleStatus", source = "item.saleStatus")
	SaleStatusResponse toSaleStatusResponse(Item item);

	/** 대표 OOTD 한 칸. 태그된 OOTD 도 같은 모양이라 함께 쓴다. */
	@Mapping(target = "postId", source = "post.id")
	@Mapping(target = "imageUrl", source = "imageUrl")
	@Mapping(target = "aspectRatio", source = "post.aspectRatio")
	FeaturedPostResponse toFeaturedPostResponse(Post post, String imageUrl);

	/** 열린 판매 등록의 조건. 없으면 Service 가 null 을 싣는다. */
	@Mapping(target = "id", source = "listing.id")
	@Mapping(target = "price", source = "price")
	@Mapping(target = "shippingFee", source = "shippingFee")
	@Mapping(target = "feeBearer", source = "listing.feeBearer")
	@Mapping(target = "sellerNote", source = "listing.sellerNote")
	@Mapping(target = "measurementScheme", source = "measurementScheme")
	@Mapping(target = "measurements", source = "measurements")
	@Mapping(target = "conditions", source = "conditions")
	ListingTermsResponse toListingTermsResponse(
			ItemListing listing, String price, String shippingFee,
			MeasurementScheme measurementScheme,
			Map<MeasurementField, BigDecimal> measurements, List<ConditionCode> conditions
	);

	@Mapping(target = "id", source = "seller.id")
	@Mapping(target = "nickname", source = "nickname")
	@Mapping(target = "profileImageUrl", source = "profileImageUrl")
	@Mapping(target = "followerCount", source = "seller.followerCount")
	@Mapping(target = "grade", source = "seller.grade")
	ListingSellerResponse toListingSellerResponse(
			User seller, String nickname, String profileImageUrl
	);

	/**
	 * 아이템 상세. 상태 변형 넷을 한 응답으로 담는다 —
	 * listing·seller 는 nullable 이고 featuredPosts·taggedPosts 는 배타적이다.
	 */
	@Mapping(target = "itemId", source = "item.id")
	@Mapping(target = "brandName", source = "item.brandName")
	@Mapping(target = "name", source = "item.name")
	@Mapping(target = "images", source = "images")
	@Mapping(target = "saleStatus", source = "item.saleStatus")
	@Mapping(target = "offerAllowed", source = "item.offerAllowed")
	@Mapping(target = "wishCount", source = "item.wishCount")
	@Mapping(target = "isWished", source = "wished")
	@Mapping(target = "categoryName", source = "item.category.name")
	@Mapping(target = "sizeLabel", source = "item.sizeLabel")
	@Mapping(target = "wearTarget", source = "item.wearTarget")
	@Mapping(target = "firstOwnedDate", source = "item.firstOwnedDate")
	@Mapping(target = "totalWearCount", source = "totalWearCount")
	@Mapping(target = "listing", source = "listing")
	@Mapping(target = "seller", source = "seller")
	@Mapping(target = "featuredPosts", source = "featuredPosts")
	@Mapping(target = "taggedPosts", source = "taggedPosts")
	@Mapping(target = "myOfferStatus", source = "myOfferStatus")
	ItemDetailResponse toItemDetailResponse(
			Item item, List<String> images, boolean wished, Integer totalWearCount,
			ListingTermsResponse listing, ListingSellerResponse seller,
			List<FeaturedPostResponse> featuredPosts, List<FeaturedPostResponse> taggedPosts,
			OfferStatus myOfferStatus
	);
}
