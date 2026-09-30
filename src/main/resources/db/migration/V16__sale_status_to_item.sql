-- 판매 상태를 item_listing 에서 item 으로 올린다 (DEC-026).
--
-- 상태를 판매 등록 행이 들고 있으면 등록이 없는 아이템은 상태를 표현할 수 없다.
-- 그런데 화면에는 그런 아이템이 둘 있다.
--   1. 미판매 공개 페이지(2825:58233) — 판매 전환을 한 번도 안 한 아이템에 '미판매' 배지가 붙는다
--   2. 판매 완료 상세 - 미판매였던 아이템(3465:33567) — 미판매 아이템이 구매 제안으로 팔린 뒤의
--      화면이다. listing 행이 없는데 '판매 완료' 를 표시한다
--
-- 2번은 trade_order.listing_id 가 NOT NULL 이라 거래를 만드는 것부터 막혀 있었다 —
-- purchase_offer.listing_id 만 nullable 로 바꾼 V14(DEC-024)가 거기까지 따라가지 못했다.
--
-- 판매중/미판매/판매완료 세 화면의 헤더가 전부 '아이템 상세' 이고 한 화면의 상태 변형으로
-- 그려진다. 상태는 아이템의 것이고 item_listing 은 '어떤 조건으로 팔았는가' 다.

-- ── item 이 판매 상태를 갖는다
ALTER TABLE `item`
  ADD COLUMN `sale_status` VARCHAR(20) NOT NULL DEFAULT 'NOT_FOR_SALE' COMMENT '히어로 하단 배지 ''판매 중'' / ''미판매'' / ''판매 완료''. 판매 등록이 없는 아이템도 표시하므로 아이템이 갖는다' AFTER `first_owned_date`;

-- 최신 판매 등록의 상태를 가져온다. 등록이 없으면 기본값 NOT_FOR_SALE 이다.
UPDATE `item` `i`
   SET `i`.`sale_status` = COALESCE((
         SELECT `l`.`sale_status`
           FROM `item_listing` `l`
          WHERE `l`.`item_id` = `i`.`id`
          ORDER BY `l`.`created_at` DESC, `l`.`id` DESC
          LIMIT 1
       ), 'NOT_FOR_SALE');

-- 검색 필터의 판매 상태 3모드(2825:38802). 상태가 아이템에 오면서 item_listing 조인이 사라졌다.
ALTER TABLE `item`
  ADD KEY `ix_item_sale_status` (`sale_status`, `deleted_at`);

-- ── item_listing 은 판매 조건 스냅샷만 갖는다
--
-- 인덱스도 함께 바꾼다. 조회가 가장 잦은 것은 '열린 판매 등록 1건' 이고,
-- 판매자별 이력은 상태로 거르지 않으므로 게시 시각으로 잡는다.
ALTER TABLE `item_listing`
  DROP KEY `ix_item_listing_item`,
  DROP KEY `ix_item_listing_seller`,
  DROP COLUMN `sale_status`,
  ADD KEY `ix_item_listing_item` (`item_id`, `closed_at`) COMMENT '열린 판매 등록 1건을 찾는 조회가 가장 잦다',
  ADD KEY `ix_item_listing_seller` (`seller_id`, `listed_at`) COMMENT '판매자별 게시 이력';

-- 가격과 게시 시각은 행이 생기는 순간 반드시 있다 — 판매 전환 폼에서 가격이 필수 표시(*)이고,
-- 미판매는 '행이 없거나 닫힌 행' 이지 '가격이 빈 행' 이 아니다.
-- 기존 행 중 값이 비어 있던 것을 먼저 메운다.
UPDATE `item_listing` SET `price` = 0 WHERE `price` IS NULL;
UPDATE `item_listing` SET `listed_at` = `created_at` WHERE `listed_at` IS NULL;

ALTER TABLE `item_listing`
  MODIFY COLUMN `price` INT NOT NULL COMMENT '''가격'' 320,000원 (필수 표시 *). 미판매는 행이 없거나 닫힌 행이지 가격이 빈 행이 아니다',
  MODIFY COLUMN `listed_at` DATETIME(6) NOT NULL COMMENT '''판매 게시가 완료됐습니다.'' — 행이 생기는 순간이 곧 게시 시각',
  MODIFY COLUMN `closed_at` DATETIME(6) NULL COMMENT '''미판매로 변경시 화면'' — 판매 내림 시각. NULL 이면 열린 판매 등록이고 아이템당 1개뿐이다';

-- ── 미판매 아이템의 구매 제안에서 시작한 거래는 판매 등록이 없다
-- purchase_offer.listing_id 가 이미 nullable 이므로(V14) 그 제안을 수락하면
-- trade_order 에 넣을 listing_id 가 없다. 판매 상태는 item 이 들고 있으니
-- 이 값이 없어도 '판매 완료' 화면이 성립한다.
ALTER TABLE `trade_order`
  MODIFY COLUMN `listing_id` BIGINT NULL COMMENT '거래가 성립한 판매 조건 스냅샷. 미판매 아이템의 구매 제안에서 시작한 거래는 NULL';

-- ── 감사 컬럼 관례에서 빠져 있던 것을 채운다
-- sort_order 는 V5 에서 이미 INT 로 넓혔다. 여기서는 근거만 주석에 남긴다.
ALTER TABLE `listing_featured_post`
  ADD COLUMN `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례' AFTER `sort_order`,
  MODIFY COLUMN `sort_order` INT NOT NULL DEFAULT 0 COMMENT '선택 화면(2825:52006)이 고른 순서를 카드 우상단에 1·2·3·4 배지로 표시한다';
