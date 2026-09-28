-- 배송지에 우편번호 컬럼을 추가한다.
--
-- 피그마 '배송지 추가'(4245:46172) 의 입력칸이 우편번호 · 도로명주소 · 상세주소 3개인데
-- V1 은 도로명주소와 상세주소만 받고 있었다. 주소 검색 결과가 우편번호를 함께 돌려주므로
-- 받을 값이 있는데 담을 자리가 없던 상태다.
--
-- order_shipment 에도 함께 넣는다. 배송지는 거래 시점에 값을 복사하는 스냅샷이라(DEC-013)
-- 한쪽만 넣으면 그 뒤의 거래부터 우편번호가 비어 버린다. 거래 데이터가 없는 지금이 가장 싸다.
-- 화면에 우편번호를 다시 보여주지는 않지만(거래 상세는 주소 · 상세주소만 표시) 택배 접수에 필요하다.
--
-- NOT NULL 을 곧바로 걸면 기존 행이 있는 DB 에서 막히므로 빈 문자열 기본값으로 채운 뒤
-- 기본값을 떼어낸다. 이후 INSERT 는 값을 반드시 넘겨야 한다.

ALTER TABLE `shipping_address`
  ADD COLUMN `zipcode` VARCHAR(10) NOT NULL DEFAULT '' COMMENT '''우편번호'' 07027. 주소 검색이 도로명주소와 함께 돌려준다' AFTER `phone`;

ALTER TABLE `shipping_address`
  ALTER COLUMN `zipcode` DROP DEFAULT;

ALTER TABLE `order_shipment`
  ADD COLUMN `zipcode` VARCHAR(10) NOT NULL DEFAULT '' COMMENT '''우편번호'' 07027 — 거래 시점 스냅샷' AFTER `phone`;

ALTER TABLE `order_shipment`
  ALTER COLUMN `zipcode` DROP DEFAULT;
