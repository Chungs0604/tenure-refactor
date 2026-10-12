package com.tenure.domain.listing.service;

import com.tenure.domain.listing.dto.response.FeaturedPostResponse;
import com.tenure.domain.listing.mapper.ListingMapper;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.repository.PostImageRepository;
import com.tenure.global.storage.FileStorage;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 게시물 목록을 대표 OOTD 카드 응답으로 조립한다. 조회(ListingQueryService)·쓰기
 * (ListingService) 양쪽이 같은 조립이 필요해서 여기로 뽑았다 — ItemFormApplier 와 같은
 * 자리의 협력자다. 트랜잭션을 열지 않고 부르는 쪽의 트랜잭션 안에서 돈다.
 */
@Component
@RequiredArgsConstructor
class FeaturedPostAssembler {

	private final PostImageRepository postImageRepository;
	private final ListingMapper listingMapper;
	private final FileStorage fileStorage;

	/** 카드마다 대표 사진을 다시 읽으면 장수만큼 쿼리가 늘어남 — 한 번에 묶어 읽는다. */
	List<FeaturedPostResponse> toCards(List<Post> posts) {
		if (posts.isEmpty()) {
			return List.of();
		}

		Map<Long, String> covers = postImageRepository
				.findCovers(posts.stream().map(Post::getId).toList()).stream()
				.collect(Collectors.toMap(
						image -> image.getPost().getId(),
						image -> fileStorage.toUrl(image.getImageKey())));

		return posts.stream()
				.map(post -> listingMapper.toFeaturedPostResponse(post, covers.get(post.getId())))
				.toList();
	}
}
