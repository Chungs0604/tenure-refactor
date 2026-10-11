-- 검색 결과 정렬(API-200)을 받쳐주는 인덱스. `V13` 이 미뤄 둔 것이다 —
-- 그때는 "검색 쿼리 형태가 확정되지 않았다"는 이유로 인덱스를 보류했는데, `api-spec.md`
-- API-200 명세가 확정되며 정렬 5종(추천 순·최신 순·좋아요 순·조회수 순·저장 순)이 굳었다.
--
-- `최신 순` 은 기존 `ix_post_feed`(deleted_at, created_at) 를 그대로 쓴다(홈 피드와 동일한
-- 접근 패턴). 나머지 넷은 쓸 수 있는 인덱스가 없었다 — `추천 순` 은 키워드가 있으면 매칭 등급을
-- 애플리케이션에서 먼저 매기고 그 안에서만 `rank_score` 로 정렬하므로 filesort 를 완전히
-- 없애진 못하지만, 키워드 없이 필터만 적용한 검색(피그마 2825:41607)에서는 이 인덱스가 그대로
-- WHERE·ORDER BY 를 전부 받는다.

ALTER TABLE `post`
  ADD INDEX `ix_post_rank_score` (`deleted_at`, `rank_score`, `id`) COMMENT '검색 추천 순 (DEC-023)',
  ADD INDEX `ix_post_like_count` (`deleted_at`, `like_count`, `id`) COMMENT '검색 좋아요 순',
  ADD INDEX `ix_post_view_count` (`deleted_at`, `view_count`, `id`) COMMENT '검색 조회수 순 (DEC-019)',
  ADD INDEX `ix_post_save_count` (`deleted_at`, `save_count`, `id`) COMMENT '검색 저장 순';
