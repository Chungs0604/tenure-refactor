-- 미판매 구매 제안 1회 제한을 DB 로도 막음
--
-- 지금까지는 '같은 아이템에 이미 쓴 기회가 있는지' 를 조회 후 저장(read-then-write) 으로만
-- 판정했음 — 같은 구매자가 같은 아이템에 동시에 두 번 요청을 보내면 둘 다 조회를 통과해
-- 중복 제안과 중복 결제 승인이 함께 생김
--
-- 기존 기본 배송지·기본 정산 계좌와 같은 방식으로 함수형 인덱스를 씀. 시스템 취소
-- (BUYER_CANCELED 가 아닌 CANCELED) 행은 식이 NULL 이 되어 자리를 비우고, 그 외엔 전부
-- (item_id, buyer_id) 조합이 값으로 나와 유니크가 걸림

ALTER TABLE `purchase_offer`
  ADD UNIQUE KEY `uk_purchase_offer_limit` (
    (IF(`offer_type` = 'PURCHASE_OFFER' AND NOT(`status` = 'CANCELED' AND `cancel_reason` <> 'BUYER_CANCELED'), `item_id`, NULL)),
    (IF(`offer_type` = 'PURCHASE_OFFER' AND NOT(`status` = 'CANCELED' AND `cancel_reason` <> 'BUYER_CANCELED'), `buyer_id`, NULL))
  );
