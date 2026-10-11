package com.tenure.domain.listing.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대표 OOTD 교체 요청. 전체 폼(ListingCreateRequest) 과 달리 대표 OOTD 만 따로 바꾸는 전용 요청이다.
 *
 * featuredPostIds 는 보낸 순서가 노출 순서가 된다 — ListingCreateRequest 와 같은 규칙이다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "대표 OOTD 교체 요청")
public class FeaturedPostsReplaceRequest {

	@Schema(description = "대표 OOTD 게시물 id. 보낸 순서가 노출 순서다", example = "[12, 34]")
	@NotEmpty
	@Size(min = 1, max = 5)
	private List<Long> featuredPostIds;
}
