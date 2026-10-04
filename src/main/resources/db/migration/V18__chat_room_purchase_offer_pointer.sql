-- 채팅방 헤더 가격 출처에 구매 제안을 추가함
--
-- chat_room.listing_id 는 판매중 아이템 채팅의 가격 출처임. 미판매 아이템에 구매 제안을 보내면
-- 가리킬 listing 이 없어 가격을 낼 곳이 없음 — 가격이 purchase_offer 에만 있음
--
-- listing_id · purchase_offer_id 는 배타적으로 씀 — 판매중이면 listing_id, 미판매+제안이면
-- purchase_offer_id, 제안 전이면 둘 다 NULL. 재제안 시 이 FK 를 최신 제안으로 갱신하며
-- 강제는 애플리케이션(offer 도메인)이 맡음

ALTER TABLE `chat_room`
  ADD COLUMN `purchase_offer_id` BIGINT COMMENT '미판매+제안 방의 가격 출처. listing_id 와 배타적임' AFTER `listing_id`,
  ADD KEY `ix_chat_room_purchase_offer` (`purchase_offer_id`) COMMENT 'FK 인덱스',
  ADD CONSTRAINT `fk_chat_room_purchase_offer_id` FOREIGN KEY (`purchase_offer_id`) REFERENCES `purchase_offer` (`id`) ON DELETE RESTRICT;
