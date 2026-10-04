-- 구매 제안에 배송지 스냅샷을 추가함
--
-- 제안 생성 요청은 shippingAddressId 를 받고, 제안서 상세는 수령인·연락처·주소를 보여줘야 하는데
-- purchase_offer 에는 그 값을 담을 컬럼이 없었음. shipping_address 를 FK 로 걸면 응답 대기 중에
-- 주소록을 수정·삭제했을 때 이미 보낸 제안의 배송지가 조용히 바뀜 — 금액처럼 생성 시점 값을
-- 그대로 떠 둬야 함
--
-- 수락 시 이 값을 order_shipment 로 그대로 복사함

ALTER TABLE `purchase_offer`
  ADD COLUMN `shipping_recipient_name` VARCHAR(50) NOT NULL COMMENT '수령인 스냅샷' AFTER `buyer_request`,
  ADD COLUMN `shipping_phone` VARCHAR(20) NOT NULL COMMENT '연락처 스냅샷' AFTER `shipping_recipient_name`,
  ADD COLUMN `shipping_zipcode` VARCHAR(10) NOT NULL COMMENT '우편번호 스냅샷' AFTER `shipping_phone`,
  ADD COLUMN `shipping_address` VARCHAR(255) NOT NULL COMMENT '주소 스냅샷' AFTER `shipping_zipcode`,
  ADD COLUMN `shipping_address_detail` VARCHAR(255) COMMENT '상세주소 스냅샷' AFTER `shipping_address`,
  ADD COLUMN `shipping_delivery_request_type` VARCHAR(30) COMMENT '배송 요청사항 유형 스냅샷' AFTER `shipping_address_detail`,
  ADD COLUMN `shipping_delivery_request` VARCHAR(50) COMMENT '배송 요청사항 직접입력 스냅샷' AFTER `shipping_delivery_request_type`;
