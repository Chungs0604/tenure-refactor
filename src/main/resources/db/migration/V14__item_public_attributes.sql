-- 공개 노출 속성을 item_listing 에서 item 으로 올린다.
--
-- item_listing 은 성격이 다른 두 가지를 함께 담고 있었다.
--   공개 노출 속성(wish_count · offer_allowed) — 아이템과 수명이 같다
--   판매 조건(price · shipping_fee · fee_bearer · seller_note · seller_id) — 판매할 때만 산다
--
-- item_listing 은 아이템이 재판매될 때마다 새 행이 생기고(1:N) 생성 전제로 정산 계좌가
-- 필요하다. 그래서 공개 속성을 여기 두면 세 가지가 깨진다.
--   1. 갓 등록한 아이템은 listing 이 없어 관심을 받을 곳이 없다 — wishlist 는
--      '판매 등록 전 아이템도 담을 수 있어야 한다'가 전제이고 위시리스트 화면에 '미판매' 배지가 있다
--   2. OfferType.PURCHASE_OFFER 의 정의가 '미판매 제품에 대한 구매제안'인데
--      purchase_offer.listing_id 가 NOT NULL 이라 보낼 대상이 없다
--   3. 판매를 내렸다 다시 걸면 새 listing 행이 생겨 관심 수가 0 으로 리셋된다
--
-- 근거 화면 — 미판매 상품 상세(2825:58233)가 '관심 5명'과 '구매 제안 O/X' 를 함께 그린다.
-- 구매제안 on(2825:57652) 에는 하단에 '구매제안하기' CTA 가 붙고 off(2825:57760) 에는 없다.

-- ── item 에 공개 노출 속성 추가
ALTER TABLE `item`
  ADD COLUMN `offer_allowed` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '''구매 제안 O'' / ''구매 제안 X'', ''구매 제안 허용'' 토글. 판매 등록과 무관하게 아이템에 붙는다' AFTER `first_owned_date`,
  ADD COLUMN `wish_count` INT NOT NULL DEFAULT 0 COMMENT '''관심 14명''. 비정규화 카운터이며 정본은 wishlist 테이블(→ 파생 컬럼 대장)' AFTER `offer_allowed`;

-- 기존 값 이관. wish_count 는 정본인 wishlist 에서 다시 세고,
-- offer_allowed 는 아이템의 최신 listing 값을 가져온다.
UPDATE `item` `i`
   SET `i`.`wish_count` = (SELECT COUNT(*) FROM `wishlist` `w` WHERE `w`.`item_id` = `i`.`id`);

UPDATE `item` `i`
   SET `i`.`offer_allowed` = COALESCE((
         SELECT `l`.`offer_allowed`
           FROM `item_listing` `l`
          WHERE `l`.`item_id` = `i`.`id`
          ORDER BY `l`.`created_at` DESC, `l`.`id` DESC
          LIMIT 1
       ), FALSE);

-- ── item_listing 은 판매 조건만 갖는다
ALTER TABLE `item_listing`
  DROP COLUMN `offer_allowed`,
  DROP COLUMN `wish_count`;

-- ── 미판매 아이템에도 구매 제안을 보낼 수 있어야 한다
-- PURCHASE_OFFER(미판매 제안)는 listing 이 없고 TRADE_INTENT(판매 중 거래 의사)만 갖는다.
-- 1회 제한 판정은 이미 item_id 기준이라 정본 키는 그대로다.
ALTER TABLE `purchase_offer`
  MODIFY COLUMN `listing_id` BIGINT NULL COMMENT '제안 대상 판매 등록. PURCHASE_OFFER(미판매 제안)는 NULL 이고 TRADE_INTENT 만 값을 갖는다';

-- ── 세부 카테고리 표기를 화면과 맞춘다 — '긴팔 티셔츠' → '긴소매 티셔츠'
-- 상의 상세 수정(2825:49332) · 아이템 상세(2825:48757) · 미판매 공개 페이지(2825:58233)
-- 세 화면이 모두 '긴소매 티셔츠' 로 쓴다. V2 seed 만 '긴팔 티셔츠' 였다.
UPDATE `category`
   SET `name` = '긴소매 티셔츠'
 WHERE `depth` = 2 AND `name` = '긴팔 티셔츠';
