-- 기본 정산 계좌가 사용자당 최대 1건임을 DB 가 강제한다.
--
-- 배송지(V7)와 같은 함수 인덱스다. MySQL 은 부분 유니크 인덱스를 지원하지 않으므로
-- 기본이면 user_id, 아니면 NULL 로 계산한 값에 UNIQUE 를 건다 — 유니크 인덱스는 NULL 중복을
-- 허용하므로 비기본 계좌는 몇 개든 쌓이고 기본은 사용자당 하나만 존재할 수 있다.
--
-- 배송지와 다른 점은 0건이 정상이라는 것뿐이다. 가입은 정산 계좌를 만들지 않고
-- 판매 전환 시점에 처음 등록한다. 함수 인덱스는 '최대 1건'만 보장하고 '최소 1건'은 보장하지 않으므로
-- 이 차이에 맞는 제약이 그대로 성립한다.
--
-- 주의 — 기존 기본을 내리는 UPDATE 가 새 행 INSERT 보다 먼저 DB 에 닿아야 한다.
-- Hibernate 는 flush 시 INSERT 를 UPDATE 보다 먼저 실행하므로, 해제는 즉시 실행되는
-- 벌크 UPDATE 로 처리한다 (PayoutAccountRepository.releaseDefault).

ALTER TABLE `payout_account`
  ADD UNIQUE KEY `uk_payout_account_default` ((IF(`is_default`, `user_id`, NULL)));
