-- 기본 배송지가 사용자당 1건임을 DB 가 강제한다.
--
-- 지금은 애플리케이션이 '기존 기본을 내리고 새 것을 올린다' 순서로만 지키고 있어,
-- isDefault=true 요청이 동시에 둘 들어오면 각자 기존 기본을 내리고 둘 다 true 가 될 수 있다.
-- is_default 를 건드리는 코드가 늘 때마다 같은 주의를 되풀이해야 하는 것도 문제다.
--
-- MySQL 은 부분 유니크 인덱스(WHERE is_default = true)를 지원하지 않으므로 함수 인덱스를 쓴다.
-- 기본이면 user_id, 아니면 NULL 로 계산한 값에 UNIQUE 를 건다 — 유니크 인덱스는 NULL 중복을
-- 허용하므로 비기본 배송지는 몇 개든 쌓이고, 기본은 사용자당 하나만 존재할 수 있다.
--
-- 주의 — 기존 기본을 내리는 UPDATE 가 새 행 INSERT 보다 먼저 DB 에 닿아야 한다.
-- Hibernate 는 flush 시 INSERT 를 UPDATE 보다 먼저 실행하므로, 해제는 즉시 실행되는
-- 벌크 UPDATE 로 처리한다 (ShippingAddressRepository.releaseDefault).

ALTER TABLE `shipping_address`
  ADD UNIQUE KEY `uk_shipping_address_default` ((IF(`is_default`, `user_id`, NULL)));
