-- SMALLINT · TINYINT 컬럼을 INT 로 넓힌다.
--
-- 자바에서 Short·Byte 는 리터럴마다 캐스팅이 필요하고 연산하면 int 로 승격돼 다루기 나쁘다.
-- DTO 는 Integer 를 쓰는데 엔티티만 좁은 타입이면 경계마다 변환이 붙고, 범위를 넘으면
-- 값이 조용히 뒤집힌다. 컬럼을 넓혀 그 변환 자체를 없앤다.
--
-- V1 은 이미 배포돼 수정하지 않는다. 기존 값은 전부 INT 범위 안이라 그대로 보존된다.

ALTER TABLE `user`
  MODIFY COLUMN `height_cm` INT COMMENT '프로필 작성 > 키 ''170cm''. 검색 필터 ''필터/키-몸무게''의 대상';

ALTER TABLE `user`
  MODIFY COLUMN `weight_kg` INT COMMENT '프로필 작성 > 몸무게 ''64kg''';

ALTER TABLE `post_image`
  MODIFY COLUMN `sort_order` INT NOT NULL COMMENT '''바로 게시한 사진들이 순서대로 보여요''';

ALTER TABLE `category`
  MODIFY COLUMN `depth` INT NOT NULL COMMENT '1=카테고리, 2=세부 카테고리';

ALTER TABLE `category`
  MODIFY COLUMN `sort_order` INT NOT NULL DEFAULT 0 COMMENT '드롭다운 노출 순서';

ALTER TABLE `item_image`
  MODIFY COLUMN `sort_order` INT NOT NULL COMMENT '노출 순서';

ALTER TABLE `listing_featured_post`
  MODIFY COLUMN `sort_order` INT NOT NULL DEFAULT 0 COMMENT '노출 순서';
