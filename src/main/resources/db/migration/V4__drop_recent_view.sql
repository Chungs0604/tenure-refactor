-- 최근 본 게시물·사용자를 Redis Sorted Set 으로 옮긴다 (DEC-014).
--
-- 조회할 때마다 쓰기가 일어나는 테이블인데 지우는 경로가 없어 영구 보관되고 있었다.
-- ZADD 가 재열람 시 score(열람 시각)만 갱신해 uk(user_id, target_type, target_id) 와 같고,
-- ZREMRANGEBYRANK 가 상한을 유지하므로 보관 기간을 따로 정할 필요가 없다.
--
-- 어떤 FK 도 이 테이블을 참조하지 않아 제거가 고립돼 있다.
DROP TABLE IF EXISTS `recent_view`;
