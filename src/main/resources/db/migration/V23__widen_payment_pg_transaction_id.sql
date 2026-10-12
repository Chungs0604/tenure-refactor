-- 토스페이먼츠 paymentKey 는 스펙상 최대 200자다. VARCHAR(100) 로는 길이 초과 값이
-- authorize() 저장 시 제약 위반으로 막힐 수 있어 넓힌다.
ALTER TABLE payment
    MODIFY COLUMN pg_transaction_id VARCHAR(200) COMMENT 'PG 연동 식별자. 화면 미표시';
