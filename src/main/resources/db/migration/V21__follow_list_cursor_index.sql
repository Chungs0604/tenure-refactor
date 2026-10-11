-- 팔로워/팔로잉 목록(API-050·051)의 커서 페이징을 받쳐주는 인덱스가 없었다
--
-- 기존 ix_follow_following(following_id) 와 uk_follow(follower_id, following_id) 는
-- following_id·follower_id 단건 필터는 받쳐주지만, 정렬 키인 created_at·id 가
-- 포함돼 있지 않아 목록 조회(WHERE following_id = ? ORDER BY created_at DESC, id DESC,
-- 또는 WHERE follower_id = ? 쪽)마다 매번 filesort 가 난다

ALTER TABLE `follow`
  ADD INDEX `ix_follow_followers_list` (`following_id`, `created_at`, `id`),
  ADD INDEX `ix_follow_followings_list` (`follower_id`, `created_at`, `id`);
