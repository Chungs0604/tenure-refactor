package com.tenure.domain.search.repository;

import com.tenure.domain.post.entity.Post;
import com.tenure.global.pagination.Cursor;

/**
 * 검색 결과 한 행 + 그 행의 커서.
 *
 * 추천 순(RECOMMENDED)의 정렬 키(매칭 등급+rank_score 복합값)는 Post 엔티티의 컬럼이 아니라
 * 쿼리 시점에 계산되는 값이라, 엔티티만 돌려주면 Service 가 커서를 다시 만들 방법이 없다
 * (다시 계산하면 SQL 과 Java 양쪽에 같은 로직이 중복된다). 그래서 레포지토리가 행마다 커서를
 * 미리 만들어 함께 돌려준다.
 */
public record PostSearchRow(Post post, Cursor cursor) {
}
