-- 대분류가 어떤 실측 항목을 받는지를 category 가 갖는다.
--
-- item_measurement 는 10개 고정 필드이고 카테고리별 차이는 '어떤 걸 채우느냐' 로만 표현된다.
-- 그런데 판정 기준을 size_scheme 으로 대신할 수 없다 — 하의와 치마가 둘 다 BOTTOM 인데
-- 실측 항목이 다르다.
--   하의  허리 단면 · 허벅지 단면 · 총 기장 · 밑위 · 인심 · 밑단      6개
--   치마  허리 단면 · 엉덩이 단면 · 밑단 · 총 기장                  4개
--
-- 판매 전환 화면(3342:32943)이 '실측 입력 · 바지' 처럼 대분류별로 다른 입력란을 그리므로
-- 프론트도 이 값이 필요하다. 사이즈 칩을 DB 로 올린 것과 같은 이유다 (DEC-025) —
-- 하드코딩하면 프론트와 서버가 각자 표를 들고 있다가 카테고리가 늘 때 양쪽이 어긋난다.

ALTER TABLE `category`
  ADD COLUMN `measurement_scheme` VARCHAR(20) COMMENT '대분류가 받는 실측 항목 세트(MeasurementScheme). 세부 카테고리는 NULL 이며 부모에서 읽는다' AFTER `size_scheme`;

-- 상의 · 아우터 · 원피스 — 어깨 너비 · 가슴 단면 · 소매길이 · 총 기장
UPDATE `category` SET `measurement_scheme` = 'TOP'    WHERE `id` IN (1, 2, 8);

-- 하의 — 허리 단면 · 허벅지 단면 · 총 기장 · 밑위 · 인심 · 밑단
UPDATE `category` SET `measurement_scheme` = 'BOTTOM' WHERE `id` = 3;

-- 치마 — 허리 단면 · 엉덩이 단면 · 밑단 · 총 기장
UPDATE `category` SET `measurement_scheme` = 'SKIRT'  WHERE `id` = 7;

-- 신발 · 가방 · 모자 · 액세서리 · 주얼리 — 실측 입력 자체가 없다. 행을 만들지 않는다
UPDATE `category` SET `measurement_scheme` = 'NONE'   WHERE `id` IN (4, 5, 6, 9, 10);
