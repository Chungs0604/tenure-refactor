-- 배송 요청사항을 드롭다운 구조로 바꾼다.
--
-- 화면(4245:46588)의 '배송 요청사항(선택)'은 자유 입력이 아니라 select 다.
--   문 앞에 놓아주세요 / 경비실에 맡겨주세요 / 택배함에 넣어주세요 / 직접 입력
-- '직접 입력'을 고를 때만 텍스트 필드가 열리고 '최대 50자까지 입력 가능'을 안내한다.
--
-- 기존 자유 텍스트는 유형을 알 수 없으므로 DIRECT 로 옮긴다 — 문구가 무엇이든 사용자가 직접 쓴 값이다.
-- 50자를 넘는 값은 잘리지 않게 먼저 줄여 둔다(실서비스 데이터가 없는 단계라 사실상 대상이 없다).
--
-- order_shipment 는 배송지 값을 복사해 갖는 스냅샷이라(DEC-013) 같은 모양으로 맞춘다.

ALTER TABLE `shipping_address`
  ADD COLUMN `delivery_request_type` VARCHAR(30) NULL COMMENT '배송 요청사항 유형(DeliveryRequestType). 미선택이면 NULL' AFTER `address_detail`;

UPDATE `shipping_address`
   SET `delivery_request_type` = 'DIRECT'
 WHERE `delivery_request` IS NOT NULL AND `delivery_request` <> '';

UPDATE `shipping_address`
   SET `delivery_request` = LEFT(`delivery_request`, 50)
 WHERE CHAR_LENGTH(`delivery_request`) > 50;

ALTER TABLE `shipping_address`
  MODIFY COLUMN `delivery_request` VARCHAR(50) NULL COMMENT '직접 입력한 요청사항. DIRECT 일 때만 채운다';

ALTER TABLE `order_shipment`
  ADD COLUMN `delivery_request_type` VARCHAR(30) NULL COMMENT '배송지의 요청사항 유형 스냅샷' AFTER `address_detail`;

UPDATE `order_shipment`
   SET `delivery_request_type` = 'DIRECT'
 WHERE `delivery_request` IS NOT NULL AND `delivery_request` <> '';

UPDATE `order_shipment`
   SET `delivery_request` = LEFT(`delivery_request`, 50)
 WHERE CHAR_LENGTH(`delivery_request`) > 50;

ALTER TABLE `order_shipment`
  MODIFY COLUMN `delivery_request` VARCHAR(50) NULL COMMENT '직접 입력값 스냅샷. DIRECT 일 때만 채워진다';
