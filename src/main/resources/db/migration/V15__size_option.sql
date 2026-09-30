-- 카테고리별 사이즈 칩 목록을 데이터로 옮긴다 (Q6 해소).
--
-- category.size_scheme 은 VARCHAR 한 칸이라 체계를 하나만 담는다. 그런데 상세 수정 바텀시트
-- 10종을 열어 보니 상의는 네 체계(XS~3XL · 85~115 · 36~50 · 0~6)를 한꺼번에 펼친다.
-- 한 칸으로는 화면을 그릴 수 없다.
--
-- size_option 이 '어떤 칩 세트가 어떤 라벨을 어떤 순서로 갖는지' 를 들고,
-- category.size_scheme 은 대분류가 어느 세트를 쓰는지만 가리킨다. 세부 카테고리는 NULL 이며
-- 부모에서 읽는다 — 전부 NULL 이던 상태와 달리 '대분류가 정본' 이라는 규칙이 생긴다.
--
-- item.size_label 은 FK 로 묶지 않고 문자열 그대로 둔다. 사이즈 체계를 나중에 손보더라도
-- 이미 등록된 아이템의 기록이 흔들리면 안 된다 — 금액·배송지를 스냅샷으로 두는 것과 같은 이유다.
-- 대신 등록·수정이 이 표로 검증한다(ITEM_400_SIZE).
--
-- 근거 화면 — 상의 2825:49332 · 아우터 2825:49452 · 원피스 2825:49575 (TOP)
--             하의 2825:49691 · 치마 2825:49812 (BOTTOM)
--             신발 2825:50263 (SHOE)
--             모자 2825:49933 · 가방 2825:50015 · 액세서리 2825:50181 · 쥬얼리 2825:50099 (ONE_SIZE)

CREATE TABLE `size_option` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `scheme` VARCHAR(20) NOT NULL COMMENT '칩 세트(SizeScheme). category.size_scheme 이 가리킨다',
  `size_system` VARCHAR(20) NOT NULL COMMENT '그 칩이 속한 체계(SizeSystem). 칩을 줄로 묶는 근거',
  `label` VARCHAR(20) NOT NULL COMMENT '화면에 그대로 쓰는 칩 라벨. item.size_label 에 이 값이 들어간다',
  `sort_order` INT NOT NULL COMMENT '칩 노출 순서',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_size_option` (`scheme`, `label`),
  KEY `ix_size_option_order` (`scheme`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '카테고리별 사이즈 칩 목록. 프론트가 하드코딩하지 않도록 API-090 이 함께 내려준다';

INSERT INTO `size_option` (`scheme`, `size_system`, `label`, `sort_order`) VALUES
  ('TOP', 'ALPHA', 'XS', 1),
  ('TOP', 'ALPHA', 'S', 2),
  ('TOP', 'ALPHA', 'M', 3),
  ('TOP', 'ALPHA', 'L', 4),
  ('TOP', 'ALPHA', 'XL', 5),
  ('TOP', 'ALPHA', '2XL', 6),
  ('TOP', 'ALPHA', '3XL', 7),
  ('TOP', 'KR_NUMERIC', '85', 8),
  ('TOP', 'KR_NUMERIC', '90', 9),
  ('TOP', 'KR_NUMERIC', '95', 10),
  ('TOP', 'KR_NUMERIC', '100', 11),
  ('TOP', 'KR_NUMERIC', '105', 12),
  ('TOP', 'KR_NUMERIC', '110', 13),
  ('TOP', 'KR_NUMERIC', '115', 14),
  ('TOP', 'EU_NUMERIC', '36', 15),
  ('TOP', 'EU_NUMERIC', '38', 16),
  ('TOP', 'EU_NUMERIC', '40', 17),
  ('TOP', 'EU_NUMERIC', '42', 18),
  ('TOP', 'EU_NUMERIC', '44', 19),
  ('TOP', 'EU_NUMERIC', '46', 20),
  ('TOP', 'EU_NUMERIC', '48', 21),
  ('TOP', 'EU_NUMERIC', '50', 22),
  ('TOP', 'FREE_NUMBER', '0', 23),
  ('TOP', 'FREE_NUMBER', '1', 24),
  ('TOP', 'FREE_NUMBER', '2', 25),
  ('TOP', 'FREE_NUMBER', '3', 26),
  ('TOP', 'FREE_NUMBER', '4', 27),
  ('TOP', 'FREE_NUMBER', '5', 28),
  ('TOP', 'FREE_NUMBER', '6', 29),
  ('BOTTOM', 'ALPHA', 'XS', 1),
  ('BOTTOM', 'ALPHA', 'S', 2),
  ('BOTTOM', 'ALPHA', 'M', 3),
  ('BOTTOM', 'ALPHA', 'L', 4),
  ('BOTTOM', 'ALPHA', 'XL', 5),
  ('BOTTOM', 'ALPHA', '2XL', 6),
  ('BOTTOM', 'ALPHA', '3XL', 7),
  ('BOTTOM', 'INCH', '24', 8),
  ('BOTTOM', 'INCH', '25', 9),
  ('BOTTOM', 'INCH', '26', 10),
  ('BOTTOM', 'INCH', '27', 11),
  ('BOTTOM', 'INCH', '28', 12),
  ('BOTTOM', 'INCH', '29', 13),
  ('BOTTOM', 'INCH', '30', 14),
  ('BOTTOM', 'INCH', '31', 15),
  ('BOTTOM', 'INCH', '32', 16),
  ('BOTTOM', 'INCH', '33', 17),
  ('BOTTOM', 'INCH', '34', 18),
  ('BOTTOM', 'INCH', '35', 19),
  ('BOTTOM', 'INCH', '36', 20),
  ('BOTTOM', 'INCH', '37', 21),
  ('BOTTOM', 'INCH', '38', 22),
  ('BOTTOM', 'INCH', '39', 23),
  ('BOTTOM', 'INCH', '40', 24),
  ('BOTTOM', 'FREE_NUMBER', '0', 25),
  ('BOTTOM', 'FREE_NUMBER', '1', 26),
  ('BOTTOM', 'FREE_NUMBER', '2', 27),
  ('BOTTOM', 'FREE_NUMBER', '3', 28),
  ('BOTTOM', 'FREE_NUMBER', '4', 29),
  ('BOTTOM', 'FREE_NUMBER', '5', 30),
  ('BOTTOM', 'FREE_NUMBER', '6', 31),
  ('SHOE', 'MM', '210', 1),
  ('SHOE', 'MM', '215', 2),
  ('SHOE', 'MM', '220', 3),
  ('SHOE', 'MM', '225', 4),
  ('SHOE', 'MM', '230', 5),
  ('SHOE', 'MM', '235', 6),
  ('SHOE', 'MM', '240', 7),
  ('SHOE', 'MM', '245', 8),
  ('SHOE', 'MM', '250', 9),
  ('SHOE', 'MM', '255', 10),
  ('SHOE', 'MM', '260', 11),
  ('SHOE', 'MM', '265', 12),
  ('SHOE', 'MM', '270', 13),
  ('SHOE', 'MM', '275', 14),
  ('SHOE', 'MM', '280', 15),
  ('SHOE', 'MM', '285', 16),
  ('SHOE', 'MM', '290', 17),
  ('SHOE', 'MM', '295', 18),
  ('SHOE', 'MM', '300', 19),
  ('SHOE', 'MM', '305', 20),
  ('SHOE', 'MM', '310', 21),
  ('ONE_SIZE', 'ONE_SIZE', 'One size', 1);

-- 대분류가 어느 칩 세트를 쓰는지. 세부 카테고리(depth=2)는 NULL 로 두고 부모에서 읽는다.
UPDATE `category` SET `size_scheme` = 'TOP'      WHERE `id` IN (1, 2, 8);
UPDATE `category` SET `size_scheme` = 'BOTTOM'   WHERE `id` IN (3, 7);
UPDATE `category` SET `size_scheme` = 'SHOE'     WHERE `id` = 4;
UPDATE `category` SET `size_scheme` = 'ONE_SIZE' WHERE `id` IN (5, 6, 9, 10);
