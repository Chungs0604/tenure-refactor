package com.tenure.domain.post.mapper;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.listing.enums.SaleStatus;
import com.tenure.domain.post.dto.response.MyPostSummaryResponse;
import com.tenure.domain.post.dto.response.PostAuthorResponse;
import com.tenure.domain.post.dto.response.PostImageResponse;
import com.tenure.domain.post.dto.response.PostResponse;
import com.tenure.domain.post.dto.response.PostSummaryResponse;
import com.tenure.domain.post.dto.response.PostTagResponse;
import com.tenure.domain.post.dto.response.TaggedItemResponse;
import com.tenure.domain.post.dto.response.UserPostSummaryResponse;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.entity.PostImage;
import com.tenure.domain.post.entity.PostItemTag;
import com.tenure.domain.post.enums.TaggedItemStatus;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.mapper.UserMapper;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 엔티티 → 응답 단방향 변환.
 *
 * 저장소 키를 URL 로 바꾸는 일은 여기서 하지 않는다 — 매핑 계층이 FileStorage 를 알면
 * 단위 테스트가 저장소 설정에 묶인다. Service 가 바꿔서 넘긴 값을 그대로 싣는다. 좋아요·저장 여부도
 * 같은 이유로 인자로 받는다 — 페이지 단위로 한 번에 조회한 결과를 Service 가 맞춰 넘긴다.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PostMapper {

	@Mapping(target = "id", source = "post.id")
	@Mapping(target = "imageUrl", source = "imageUrl")
	@Mapping(target = "isLiked", source = "liked")
	@Mapping(target = "isSaved", source = "saved")
	PostSummaryResponse toPostSummaryResponse(
			Post post, String imageUrl, boolean liked, boolean saved
	);

	@Mapping(target = "id", source = "post.id")
	@Mapping(target = "author", source = "author")
	@Mapping(target = "images", source = "images")
	@Mapping(target = "isFollowing", source = "following")
	@Mapping(target = "isLiked", source = "liked")
	@Mapping(target = "isSaved", source = "saved")
	PostResponse toPostResponse(
			Post post, PostAuthorResponse author, List<PostImageResponse> images,
			boolean following, boolean liked, boolean saved
	);

	@Mapping(target = "nickname", expression = "java(displayNickname(author))")
	@Mapping(target = "profileImageUrl", source = "profileImageUrl")
	PostAuthorResponse toAuthorResponse(User author, String profileImageUrl);

	@Mapping(target = "id", source = "image.id")
	@Mapping(target = "url", source = "url")
	@Mapping(target = "tags", source = "tags")
	PostImageResponse toPostImageResponse(PostImage image, String url, List<PostTagResponse> tags);

	@Mapping(target = "postItemTagId", source = "tag.id")
	@Mapping(target = "itemId", source = "tag.item.id")
	@Mapping(target = "itemName", source = "tag.item.name")
	@Mapping(target = "brandName", source = "tag.item.brandName")
	@Mapping(target = "saleStatus", source = "saleStatus")
	@Mapping(target = "posX", source = "tag.posX")
	@Mapping(target = "posY", source = "tag.posY")
	PostTagResponse toPostTagResponse(PostItemTag tag, SaleStatus saleStatus);

	@Mapping(target = "id", source = "post.id")
	@Mapping(target = "imageUrl", source = "imageUrl")
	@Mapping(target = "isLiked", source = "liked")
	@Mapping(target = "isSaved", source = "saved")
	MyPostSummaryResponse toMyPostSummaryResponse(
			Post post, String imageUrl, boolean liked, boolean saved
	);

	@Mapping(target = "id", source = "post.id")
	@Mapping(target = "imageUrl", source = "imageUrl")
	@Mapping(target = "isLiked", source = "liked")
	@Mapping(target = "isSaved", source = "saved")
	UserPostSummaryResponse toUserPostSummaryResponse(
			Post post, String imageUrl, boolean liked, boolean saved
	);

	@Mapping(target = "itemId", source = "item.id")
	@Mapping(target = "listingId", source = "listing.id")
	@Mapping(target = "brandName", source = "item.brandName")
	@Mapping(target = "name", source = "item.name")
	@Mapping(target = "coverImageUrl", source = "coverImageUrl")
	@Mapping(target = "status", source = "status")
	@Mapping(target = "price", expression = "java(formatPrice(listing))")
	@Mapping(target = "offerAllowed",
			expression = "java(listing != null && Boolean.TRUE.equals(listing.getOfferAllowed()))")
	@Mapping(target = "alarmEnabled", source = "alarmEnabled")
	TaggedItemResponse toTaggedItemResponse(
			Item item, ItemListing listing, String coverImageUrl,
			TaggedItemStatus status, boolean alarmEnabled
	);

	/** 금액은 문자열로 내린다 — 화면이 천 단위 구분만 붙이고 계산하지 않는다. */
	default String formatPrice(ItemListing listing) {
		return (listing == null || listing.getPrice() == null)
				? null
				: String.valueOf(listing.getPrice());
	}

	/** 탈퇴 계정은 저장된 닉네임 대신 고정 문구를 보여준다 — 표기를 UserMapper 와 맞춘다. */
	default String displayNickname(User author) {
		return author.isWithdrawn() ? UserMapper.WITHDRAWN_NICKNAME : author.getNickname();
	}
}
