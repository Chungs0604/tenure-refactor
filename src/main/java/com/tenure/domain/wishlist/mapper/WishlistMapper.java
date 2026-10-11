package com.tenure.domain.wishlist.mapper;

import com.tenure.domain.wishlist.dto.response.WishCountResponse;
import com.tenure.domain.wishlist.dto.response.WishlistItemResponse;
import com.tenure.domain.wishlist.entity.Wishlist;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 엔티티 → 응답 단방향 변환.
 *
 * 저장소 키를 URL 로 바꾸는 일과 판매자·경과시간 계산은 여기서 하지 않음 — Service 가
 * 바꿔서 넘긴 값을 그대로 실음(ListingMapper 와 같은 이유).
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WishlistMapper {

	@Mapping(target = "wishlistId", source = "wishlist.id")
	@Mapping(target = "itemId", source = "wishlist.item.id")
	@Mapping(target = "brandName", source = "wishlist.item.brandName")
	@Mapping(target = "name", source = "wishlist.item.name")
	@Mapping(target = "coverImageUrl", source = "coverImageUrl")
	@Mapping(target = "price", source = "price")
	@Mapping(target = "saleStatus", source = "wishlist.item.saleStatus")
	@Mapping(target = "sellerNickname", source = "sellerNickname")
	@Mapping(target = "listedAgo", source = "listedAgo")
	@Mapping(target = "alarmEnabled", source = "wishlist.alarmEnabled")
	@Mapping(target = "offerAllowed", source = "wishlist.item.offerAllowed")
	WishlistItemResponse toResponse(
			Wishlist wishlist, String coverImageUrl, String price,
			String sellerNickname, String listedAgo
	);

	@Mapping(target = "wishCount", source = "wishCount")
	WishCountResponse toWishCountResponse(Integer wishCount);
}
