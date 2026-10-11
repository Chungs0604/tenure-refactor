package com.tenure.domain.search.dto;

import com.tenure.domain.search.enums.AuthorGenderFilter;
import com.tenure.domain.search.enums.PostSortType;
import com.tenure.domain.search.enums.SaleStatusFilter;
import java.util.List;

/**
 * API-200 의 필터·정렬 조건을 한데 묶는다. 전송 형식(쿼리 파라미터)은 Controller 가 풀고,
 * 여기서부터는 값만 오간다 — 매개변수 9개를 그대로 서비스·레포지토리에 늘어놓지 않기 위함이다.
 */
public record PostSearchCriteria(
		String keyword,
		SaleStatusFilter saleStatus,
		AuthorGenderFilter authorGender,
		List<Long> categoryIds,
		Integer heightMin,
		Integer heightMax,
		Integer weightMin,
		Integer weightMax,
		PostSortType sort) {
}
