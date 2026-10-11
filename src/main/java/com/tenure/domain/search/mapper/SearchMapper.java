package com.tenure.domain.search.mapper;

import com.tenure.domain.post.entity.Post;
import com.tenure.domain.search.dto.response.SearchPostSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 엔티티 → 응답 단방향 변환. 검색 결과·추천 3섹션(유사·인기·최신)이 전부 이 모양을 쓴다.
 *
 * 최근 검색어·최근 본 사용자·추천 검색어 응답은 저장소가 둘(RDB·Redis)로 갈리는 조립이라
 * Service 가 직접 빌더로 만든다 — 매퍼로 떼어내도 재사용할 변환 로직이 없다.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SearchMapper {

	@Mapping(target = "id", source = "post.id")
	@Mapping(target = "imageUrl", source = "imageUrl")
	SearchPostSummaryResponse toSearchPostSummaryResponse(Post post, String imageUrl);
}
